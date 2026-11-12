"""Bounded FP32 binary U-Net training: python -m app.training.train --help.

Only locally generated training state is saved in checkpoints/last.pt. This
module deliberately has no arbitrary pickle/checkpoint loading or resume path;
inference consumes the separately exported, validated model bundle.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import logging
import math
import platform
import random
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import torch
from torch.utils.data import DataLoader

from app.inference.engines.unet_model import UNet2D
from app.inference.model_bundle import export_bundle
from app.preprocessing.transforms import restore_probabilities
from app.training.data import SegmentationDataset
from app.training.losses import BCEDiceLoss
from app.training.metrics import aggregate_metrics, binary_metrics

LOGGER = logging.getLogger(__name__)
MAX_EPOCHS = 1000


@dataclass(frozen=True)
class TrainConfig:
    manifest_path: Path
    output_dir: Path
    epochs: int = 20
    batch_size: int = 4
    image_size: int = 256
    base_channels: int = 32
    learning_rate: float = 1e-3
    seed: int = 42
    device: str = "auto"
    patience: int | None = None
    augment: bool = False
    threshold: float = 0.5
    plots: bool = True

    def validate(self) -> None:
        if not 1 <= self.epochs <= MAX_EPOCHS:
            raise ValueError(f"epochs must be between 1 and {MAX_EPOCHS}")
        if self.batch_size < 1:
            raise ValueError("batch_size must be positive")
        if not 8 <= self.base_channels <= 128:
            raise ValueError("base_channels must be between 8 and 128 for model bundle export")
        if not 32 <= self.image_size <= 4096:
            raise ValueError(
                "image_size must be at least 32 and at most 4096 for model bundle export"
            )
        if not math.isfinite(self.learning_rate) or self.learning_rate <= 0:
            raise ValueError("learning_rate must be finite and positive")
        if not 0 <= self.seed < 2**32:
            raise ValueError("seed must be between 0 and 2**32 - 1")
        if self.patience is not None and self.patience < 1:
            raise ValueError("patience must be positive, or omitted")
        validate_threshold(self.threshold)


def validate_threshold(threshold: float) -> None:
    if not math.isfinite(threshold) or not 0 < threshold < 1:
        raise ValueError("The foreground probability threshold must be strictly between 0 and 1")


def resolve_device(requested: str = "auto") -> torch.device:
    """Auto prefers MPS, then CPU; an unavailable explicit device is an error."""
    requested = requested.lower()
    if requested not in {"auto", "mps", "cpu"}:
        raise ValueError("device must be auto, mps, or cpu")
    mps_available = hasattr(torch.backends, "mps") and torch.backends.mps.is_available()
    if requested == "mps" and not mps_available:
        raise RuntimeError("MPS was explicitly requested but is unavailable; use --device cpu")
    selected = "mps" if requested != "cpu" and mps_available else "cpu"
    reason = (
        "MPS available"
        if selected == "mps"
        else ("explicit CPU" if requested == "cpu" else "MPS unavailable; auto uses CPU")
    )
    LOGGER.info(
        "device requested=%s selected=%s precision=float32 (%s)", requested, selected, reason
    )
    return torch.device(selected)


def validate_manifest(manifest_path: Path, data_root: Path) -> dict[str, Any]:
    """Seal provenance and reject split leakage, including renamed image copies.

    Dataset construction owns schema, masks and image validation. These checks
    additionally cover *all* splits, not only the one being loaded. Leakage checks
    compare image content (identical empty masks are legitimate). A separate
    image/mask asset digest detects changed files under an unchanged manifest.
    """
    raw = Path(manifest_path).read_bytes()
    document = json.loads(raw)
    records = document.get("records", [])
    if not records:
        raise ValueError("The data manifest must contain records")
    source = document.get("source", {})
    if source.get("verification") != "user-confirmed":
        raise ValueError("Dataset source/license verification must be user-confirmed")
    for field in ("url", "license"):
        if not isinstance(source.get(field), str) or not source[field].strip():
            raise ValueError(f"Dataset source must declare {field}")
    root = Path(data_root).resolve()
    seen_ids: set[str] = set()
    groups: dict[str, str] = {}
    assets: dict[Path, str] = {}
    asset_digests: dict[str, str] = {}
    images: dict[str, str] = {}
    split_ids: dict[str, list[str]] = {"train": [], "val": [], "test": []}
    for record in records:
        split, sample_id = record["split"], record["id"]
        if split not in split_ids:
            raise ValueError(f"Unknown manifest split: {split!r}")
        if sample_id in seen_ids:
            raise ValueError(f"Duplicate image id in manifest: {sample_id}")
        seen_ids.add(sample_id)
        split_ids[split].append(sample_id)
        group = record.get("group_id")
        if group is not None:
            if group in groups and groups[group] != split:
                raise ValueError(f"Group {group!r} overlaps train/val/test splits")
            groups[group] = split
        masks = record.get("masks", [])
        if isinstance(masks, str):
            masks = [masks]
        for asset in [record["image"], *masks]:
            path = (root / asset).resolve()
            if not path.is_relative_to(root):
                raise ValueError(f"Manifest asset escapes data_root: {asset}")
            if path in assets and assets[path] != split:
                raise ValueError(f"Image/mask path overlaps splits: {asset}")
            assets[path] = split
            if asset not in asset_digests:
                asset_digests[asset] = hashlib.sha256(path.read_bytes()).hexdigest()
        digest = asset_digests[record["image"]]
        if digest in images and images[digest] != split:
            raise ValueError(
                f"Image content overlaps splits (possibly a renamed copy): {sample_id}"
            )
        images[digest] = split
    independence_verified = document.get("patient_independence_verified", False)
    if not independence_verified:
        LOGGER.warning(
            "Patient independence is NOT verified; image/group checks are not proof of it"
        )
    return {
        "manifest_path": str(Path(manifest_path).resolve()),
        "manifest_sha256": hashlib.sha256(raw).hexdigest(),
        "dataset_sha256": hashlib.sha256(
            json.dumps(asset_digests, sort_keys=True).encode("utf-8")
        ).hexdigest(),
        "source": source,
        "patient_independence_verified": independence_verified,
        "split_ids": split_ids,
        "split_counts": {split: len(ids) for split, ids in split_ids.items()},
    }


def write_json(path: Path, document: Any) -> None:
    path.write_text(json.dumps(document, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def _write_history(path: Path, history: list[dict[str, Any]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(history[0]))
        writer.writeheader()
        writer.writerows(history)


def _plot_history(history: list[dict[str, Any]], path: Path) -> None:
    # Lazy and headless: --no-plots permits CPU smoke tests without matplotlib.
    try:
        from matplotlib.backends.backend_agg import FigureCanvasAgg
        from matplotlib.figure import Figure
    except ModuleNotFoundError as error:
        raise RuntimeError(
            "Training curves require matplotlib (training extra), or --no-plots"
        ) from error
    figure = Figure(figsize=(10, 4))
    FigureCanvasAgg(figure)
    loss_axes, dice_axes = figure.subplots(1, 2)
    epochs = [row["epoch"] for row in history]
    for key, label in (("train_loss", "train"), ("val_loss", "validation")):
        loss_axes.plot(epochs, [row[key] for row in history], label=label)
    for key, label in (
        ("train_dice", "train (resized masks)"),
        ("val_dice", "validation (original size)"),
    ):
        dice_axes.plot(epochs, [row[key] for row in history], label=label)
    loss_axes.set_ylabel("BCE + soft Dice loss")
    dice_axes.set_ylabel("Image-macro binary Dice")
    dice_axes.set_ylim(0, 1)
    for axes in (loss_axes, dice_axes):
        axes.set_xlabel("Epoch")
        axes.legend()
    figure.tight_layout()
    figure.savefig(path)


def _train_epoch(
    model: UNet2D,
    loader: DataLoader,
    optimizer: torch.optim.Optimizer,
    criterion: BCEDiceLoss,
    device: torch.device,
    threshold: float,
) -> tuple[float, float]:
    model.train()
    loss_sum, dice_sum, count = 0.0, 0.0, 0
    for batch in loader:
        images = batch["image"].to(device=device, dtype=torch.float32)
        targets = batch["mask"].to(device=device, dtype=torch.float32)
        optimizer.zero_grad(set_to_none=True)
        logits = model(images)
        loss = criterion(logits, targets)
        if not torch.isfinite(loss).item():
            raise RuntimeError(
                "Non-finite training loss; stopping without exporting invalid weights"
            )
        loss.backward()
        optimizer.step()
        with torch.no_grad():
            predicted = torch.sigmoid(logits) >= threshold
            truth = targets >= 0.5
            dimensions = (1, 2, 3)
            denominator = predicted.sum(dim=dimensions) + truth.sum(dim=dimensions)
            intersection = (predicted & truth).sum(dim=dimensions)
            dice = torch.where(denominator == 0, 1.0, 2.0 * intersection / denominator.clamp_min(1))
        size = images.shape[0]
        count += size
        loss_sum += float(loss.detach().cpu()) * size
        dice_sum += float(dice.sum().cpu())
    return loss_sum / count, dice_sum / count


def _validate_epoch(
    model: UNet2D,
    loader: DataLoader,
    dataset: SegmentationDataset,
    criterion: BCEDiceLoss,
    device: torch.device,
    threshold: float,
) -> tuple[float, dict[str, Any]]:
    model.eval()
    loss_sum, count = 0.0, 0
    rows = []
    with torch.inference_mode():
        for batch in loader:
            images = batch["image"].to(device=device, dtype=torch.float32)
            targets = batch["mask"].to(device=device, dtype=torch.float32)
            logits = model(images)
            loss = criterion(logits, targets)
            if not torch.isfinite(loss).item():
                raise RuntimeError("Non-finite validation loss")
            probabilities = torch.sigmoid(logits[:, 0]).cpu().numpy().astype(np.float32)
            loss_sum += float(loss.cpu()) * images.shape[0]
            for probability in probabilities:
                _, target = dataset.load_original(count)
                height, width = target.shape
                restored = restore_probabilities(probability, height, width)
                rows.append(binary_metrics(restored >= threshold, target))
                count += 1
    return loss_sum / count, aggregate_metrics(rows)


def train_model(config: TrainConfig) -> dict[str, Any]:
    """Train on train, select strictly improving original-size val Dice, never test."""
    config.validate()
    device = resolve_device(config.device)
    random.seed(config.seed)
    np.random.seed(config.seed)
    torch.manual_seed(config.seed)
    dataset_options = {
        "input_height": config.image_size,
        "input_width": config.image_size,
        "in_channels": 1,
        "seed": config.seed,
    }
    train_data = SegmentationDataset(
        config.manifest_path,
        "train",
        augment=config.augment,
        **dataset_options,
    )
    val_data = SegmentationDataset(config.manifest_path, "val", **dataset_options)
    provenance = validate_manifest(config.manifest_path, train_data.data_root)
    if not len(train_data) or not len(val_data):
        raise ValueError("Training requires nonempty, disjoint train and val splits")
    output = Path(config.output_dir)
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise FileExistsError("Training output must be a new or empty directory")
    output.mkdir(parents=True, exist_ok=True)
    checkpoints = output / "checkpoints"
    checkpoints.mkdir(exist_ok=True)
    generator = torch.Generator().manual_seed(config.seed)
    train_loader = DataLoader(
        train_data,
        batch_size=config.batch_size,
        shuffle=True,
        generator=generator,
        num_workers=0,
    )
    val_loader = DataLoader(val_data, batch_size=config.batch_size, shuffle=False, num_workers=0)
    model = UNet2D(in_channels=1, out_channels=1, base_channels=config.base_channels)
    model = model.to(device=device, dtype=torch.float32)
    optimizer = torch.optim.Adam(model.parameters(), lr=config.learning_rate)
    criterion = BCEDiceLoss()
    model_config = {
        "in_channels": 1,
        "output_classes": 1,
        "base_channels": config.base_channels,
        "input_height": config.image_size,
        "input_width": config.image_size,
        "supported_image_types": ["ULTRASOUND"],
    }
    configuration = {
        **asdict(config),
        "manifest_path": str(Path(config.manifest_path).resolve()),
        "output_dir": str(output.resolve()),
    }
    run = {
        "status": "running",
        "intended_use": "research",
        "config": configuration,
        **provenance,
        "device": str(device),
        "precision": "float32",
        "loss": "BCEWithLogitsLoss + mean per-image soft Dice (smooth=1)",
        "optimizer": {"name": "Adam", "learning_rate": config.learning_rate},
        "selection": {
            "split": "val",
            "metric": "macro_dice",
            "space": "original_size",
            "threshold": config.threshold,
            "ties": "earliest epoch",
        },
        "versions": {
            "python": platform.python_version(),
            "torch": str(torch.__version__),
            "numpy": np.__version__,
        },
        "checkpoint_policy": "Trusted-local last.pt only; no arbitrary checkpoint loading/resume",
        "started_at": datetime.now(timezone.utc).isoformat(),
        "history": [],
    }
    history: list[dict[str, Any]] = []
    best_score, best_epoch, stale_epochs = -1.0, 0, 0
    write_json(output / "run.json", run)
    for epoch in range(1, config.epochs + 1):
        start = time.perf_counter()
        set_epoch = getattr(train_data, "set_epoch", None)
        if callable(set_epoch):
            set_epoch(epoch)
        train_loss, train_dice = _train_epoch(
            model,
            train_loader,
            optimizer,
            criterion,
            device,
            config.threshold,
        )
        val_loss, validation = _validate_epoch(
            model,
            val_loader,
            val_data,
            criterion,
            device,
            config.threshold,
        )
        score = validation["macro_dice"]
        history.append(
            {
                "epoch": epoch,
                "train_loss": train_loss,
                "val_loss": val_loss,
                "train_dice": train_dice,
                "val_dice": score,
                "val_iou": validation["macro_iou"],
                "val_precision": validation["macro_precision"],
                "val_recall": validation["macro_recall"],
                "val_positive_dice": validation["positive_cohort"]["macro_dice"],
                "val_empty_gt_false_positive_rate": validation["empty_gt_false_positive_rate"],
                "seconds": time.perf_counter() - start,
            }
        )
        if score > best_score:
            best_score, best_epoch, stale_epochs = score, epoch, 0
            training = {
                **provenance,
                "seed": config.seed,
                "epoch": epoch,
                "validation_metrics": validation,
                "validation_loss": val_loss,
                "train_loss": train_loss,
                "config": configuration,
                "device": str(device),
                "precision": "float32",
                "optimizer": run["optimizer"],
                "loss": run["loss"],
                "versions": run["versions"],
                "selection": run["selection"],
                "started_at": run["started_at"],
                "qualification": "Research/coursework only; no clinical qualification",
            }
            bundle_path = export_bundle(
                model,
                output / "best_model",
                model_config=model_config,
                training=training,
                threshold=config.threshold,
            )
            run.update(
                best_epoch=epoch,
                best_validation_metrics=validation,
                bundle_manifest=str(bundle_path.resolve()),
            )
        else:
            stale_epochs += 1
        torch.save(
            {
                "model_state_dict": model.state_dict(),
                "optimizer_state_dict": optimizer.state_dict(),
                "epoch": epoch,
                "best_epoch": best_epoch,
                "best_validation_dice": best_score,
                "seed": config.seed,
                "manifest_sha256": provenance["manifest_sha256"],
                "dataset_sha256": provenance["dataset_sha256"],
                "config": configuration,
            },
            checkpoints / "last.pt",
        )
        run.update(history=history, epochs_completed=epoch)
        _write_history(output / "history.csv", history)
        write_json(output / "history.json", history)
        write_json(output / "run.json", run)
        LOGGER.info(
            "epoch=%d/%d train_loss=%.6f val_loss=%.6f val_dice=%.6f best_epoch=%d",
            epoch,
            config.epochs,
            train_loss,
            val_loss,
            score,
            best_epoch,
        )
        if config.patience is not None and stale_epochs >= config.patience:
            LOGGER.info("Early stopping after %d epochs without val Dice improvement", stale_epochs)
            break
    run.update(
        status="completed",
        stopped_early=len(history) < config.epochs,
        completed_at=datetime.now(timezone.utc).isoformat(),
    )
    if config.plots:
        _plot_history(history, output / "curves.png")
    write_json(output / "run.json", run)
    return run


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--image-size", type=int, default=256)
    parser.add_argument("--base-channels", type=int, default=32)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", choices=("auto", "mps", "cpu"), default="auto")
    parser.add_argument("--patience", type=int)
    parser.add_argument(
        "--augment", action="store_true", help="Paired train-only image/mask transforms"
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.5,
        help="Predeclared validation threshold, exported for hold-out evaluation",
    )
    parser.add_argument("--no-plots", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    train_model(
        TrainConfig(
            manifest_path=args.manifest,
            output_dir=args.output_dir,
            epochs=args.epochs,
            batch_size=args.batch_size,
            image_size=args.image_size,
            base_channels=args.base_channels,
            learning_rate=args.learning_rate,
            seed=args.seed,
            device=args.device,
            patience=args.patience,
            augment=args.augment,
            threshold=args.threshold,
            plots=not args.no_plots,
        )
    )


if __name__ == "__main__":
    main()
