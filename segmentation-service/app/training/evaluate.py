"""Original-size hold-out evaluation: python -m app.training.evaluate --help.

Test thresholds come only from the bundle. Otsu is an image-only threshold
baseline, with explicitly predeclared polarity or polarity selected on val.
Metrics are research measurements, not diagnostic accuracy or confidence.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import torch
from PIL import Image, ImageDraw

from app.inference.model_bundle import load_bundle
from app.preprocessing.transforms import prepare_tensor, restore_probabilities
from app.training.data import SegmentationDataset
from app.training.metrics import aggregate_metrics, binary_metrics, remove_small_components
from app.training.train import resolve_device, validate_manifest, validate_threshold, write_json

LOGGER = logging.getLogger(__name__)


def otsu_mask(gray: np.ndarray, polarity: str) -> tuple[np.ndarray, float]:
    """256-bin Otsu; dark uses <= threshold, bright uses > threshold.

    Quantization is confined to the baseline histogram, never U-Net probability
    restoration. The threshold uses image intensities only, not GT labels.
    """
    if polarity not in {"dark", "bright"}:
        raise ValueError("Otsu polarity must be dark or bright")
    gray = np.asarray(gray, dtype=np.float32)
    if gray.ndim != 2 or not gray.size or not np.isfinite(gray).all():
        raise ValueError("Otsu expects a finite, nonempty grayscale HW array")
    if gray.min() < 0 or gray.max() > 1:
        raise ValueError("Otsu grayscale values must be in [0, 1]")
    quantized = np.rint(gray * 255).astype(np.uint8)
    histogram = np.bincount(quantized.ravel(), minlength=256).astype(np.float64)
    weight = histogram.cumsum()
    moment = (histogram * np.arange(256)).cumsum()
    denominator = weight * (gray.size - weight)
    variance = np.zeros(256, dtype=np.float64)
    valid = denominator > 0
    variance[valid] = (moment[-1] * weight[valid] - moment[valid] * gray.size) ** 2
    variance[valid] /= denominator[valid]
    # For a constant image, define the threshold as its sole intensity bin.
    bin_index = int(variance.argmax()) if valid.any() else int(quantized.flat[0])
    mask = quantized <= bin_index if polarity == "dark" else quantized > bin_index
    return mask.astype(np.uint8), bin_index / 255.0


def predict_probabilities(
    model: torch.nn.Module,
    gray: np.ndarray,
    model_config: dict[str, Any],
    device: torch.device,
) -> np.ndarray:
    """Use the inference/training preprocessing contract and float restoration."""
    tensor = prepare_tensor(
        gray,
        model_config["input_height"],
        model_config["input_width"],
        channels=model_config["in_channels"],
    )
    batch = torch.from_numpy(tensor).unsqueeze(0).to(device=device, dtype=torch.float32)
    with torch.inference_mode():
        logits = model(batch)
        if logits.ndim != 4 or tuple(logits.shape[:2]) != (1, 1):
            raise ValueError("Evaluation requires binary N1HW model logits")
        probability = torch.sigmoid(logits[0, 0]).cpu().numpy().astype(np.float32)
    restored = restore_probabilities(probability, gray.shape[0], gray.shape[1])
    if not np.isfinite(restored).all():
        raise RuntimeError("Model produced non-finite probabilities; evaluation stopped")
    return restored


def _choose_otsu_polarity(dataset: SegmentationDataset) -> dict[str, Any]:
    if not len(dataset):
        raise ValueError("Otsu validation polarity selection requires a nonempty val split")
    scores: dict[str, float] = {}
    for polarity in ("dark", "bright"):
        rows = []
        for index in range(len(dataset)):
            gray, target = dataset.load_original(index)
            prediction, _ = otsu_mask(gray, polarity)
            rows.append(binary_metrics(prediction, target))
        scores[polarity] = aggregate_metrics(rows)["macro_dice"]
    # Deterministic tie-break; the test cohort is never consulted.
    chosen = "dark" if scores["dark"] >= scores["bright"] else "bright"
    return {
        "polarity": chosen,
        "polarity_selection": "validation_macro_dice",
        "selection_split": "val",
        "validation_macro_dice": scores,
        "tie_break": "dark",
    }


def _save_overlay(
    path: Path,
    gray: np.ndarray,
    target: np.ndarray,
    predictions: dict[str, np.ndarray],
) -> None:
    base = np.repeat(np.rint(gray * 255).astype(np.uint8)[..., None], 3, axis=2)
    panels = [("image", None), ("ground truth (green)", None), *predictions.items()]
    height, width = gray.shape
    canvas = Image.new("RGB", (width * len(panels), height + 24), color="black")
    draw = ImageDraw.Draw(canvas)
    for column, (label, prediction) in enumerate(panels):
        colored = base.astype(np.float32)
        if column > 0:
            truth = target.astype(bool)
            colored[truth] = 0.6 * colored[truth] + 0.4 * np.array([0, 255, 0])
        if prediction is not None:
            foreground = prediction.astype(bool)
            colored[foreground] = 0.6 * colored[foreground] + 0.4 * np.array([255, 0, 0])
        canvas.paste(Image.fromarray(np.rint(colored).astype(np.uint8)), (column * width, 24))
        draw.text((column * width + 2, 4), label, fill="white")
    canvas.save(path)


def evaluate_model(
    bundle_manifest: Path,
    manifest_path: Path,
    output_dir: Path,
    *,
    split: str = "test",
    device: str = "auto",
    threshold: float | None = None,
    min_region_pixels: int = 16,
    otsu_polarity: str = "dark",
    max_examples: int = 8,
) -> dict[str, Any]:
    """Compare raw U-Net, four-connected BFS cleanup and an Otsu baseline.

    A sealed manifest identical to the training manifest is required. This keeps
    hold-out IDs/groups/images disjoint even if someone edits a split after
    training. A new external cohort needs its own explicit provenance workflow.
    """
    if split not in {"val", "test"}:
        raise ValueError("Evaluation split must be val or test, never train")
    if threshold is not None and split != "val":
        raise ValueError(
            "CLI threshold overrides are validation-only; test uses the bundle threshold"
        )
    if threshold is not None:
        validate_threshold(threshold)
    if min_region_pixels < 1 or max_examples < 0:
        raise ValueError("min_region_pixels must be positive and max_examples nonnegative")
    if otsu_polarity not in {"dark", "bright", "validation"}:
        raise ValueError("otsu_polarity must be dark, bright, or validation")
    selected_device = resolve_device(device)
    bundle_path = Path(bundle_manifest).resolve()
    model, bundle = load_bundle(bundle_path, device=str(selected_device))
    model.to(device=selected_device, dtype=torch.float32)
    model.eval()
    model_config = bundle["model_config"]
    if model_config["in_channels"] != 1 or model_config["output_classes"] != 1:
        raise ValueError("Evaluation supports only one-channel binary U-Net bundles")
    dataset_options = {
        "input_height": model_config["input_height"],
        "input_width": model_config["input_width"],
        "in_channels": 1,
        "augment": False,
    }
    dataset = SegmentationDataset(manifest_path, split, **dataset_options)
    provenance = validate_manifest(manifest_path, dataset.data_root)
    if not len(dataset):
        raise ValueError(f"Evaluation requires a nonempty {split} split")
    sealed_digest = bundle.get("training", {}).get("manifest_sha256")
    if not sealed_digest or sealed_digest != provenance["manifest_sha256"]:
        raise ValueError(
            "Data manifest does not match the bundle's training manifest_sha256; "
            "hold-out split provenance cannot be established"
        )
    sealed_assets = bundle["training"].get("dataset_sha256")
    if sealed_assets and sealed_assets != provenance["dataset_sha256"]:
        raise ValueError(
            "Dataset image/mask files changed since training (dataset_sha256 mismatch)"
        )
    effective_threshold = bundle["threshold"] if threshold is None else threshold
    validate_threshold(effective_threshold)
    if otsu_polarity == "validation":
        validation = SegmentationDataset(manifest_path, "val", **dataset_options)
        baseline = _choose_otsu_polarity(validation)
    else:
        baseline = {
            "polarity": otsu_polarity,
            "polarity_selection": "predeclared",
            "selection_split": None,
        }
    baseline.update(
        name="Otsu",
        bins=256,
        per_image_threshold="image intensities only",
        comparison="dark <= threshold; bright > threshold",
    )
    output = Path(output_dir)
    if (output / "evaluation.json").exists() or (output / "per_image.csv").exists():
        raise FileExistsError("Evaluation output already exists; choose a new output directory")
    output.mkdir(parents=True, exist_ok=True)
    examples_dir = output / "examples"
    if max_examples:
        examples_dir.mkdir(exist_ok=True)
    records = json.loads(Path(manifest_path).read_text(encoding="utf-8"))["records"]
    by_id = {record["id"]: record for record in records}
    rows: list[dict[str, Any]] = []
    examples = []
    for index in range(len(dataset)):
        # __getitem__ supplies the authoritative id without assuming a particular
        # representation/order for the dataset's public .records collection.
        sample_id = dataset[index]["id"]
        record = by_id[sample_id]
        gray, target = dataset.load_original(index)
        probability = predict_probabilities(model, gray, model_config, selected_device)
        raw = (probability >= effective_threshold).astype(np.uint8)
        cleaned = remove_small_components(raw, min_region_pixels)
        otsu, baseline_threshold = otsu_mask(gray, baseline["polarity"])
        predictions = {"unet_raw": raw, "unet_bfs": cleaned, "otsu": otsu}
        for method, prediction in predictions.items():
            rows.append(
                {
                    "id": sample_id,
                    "category": record.get("category"),
                    "group_id": record.get("group_id"),
                    "split": split,
                    "method": method,
                    "height": int(target.shape[0]),
                    "width": int(target.shape[1]),
                    "threshold": baseline_threshold if method == "otsu" else effective_threshold,
                    **binary_metrics(prediction, target),
                }
            )
        if index < max_examples:
            relative_path = Path("examples") / f"{index:04d}.png"
            _save_overlay(output / relative_path, gray, target, predictions)
            examples.append({"id": sample_id, "path": relative_path.as_posix()})
    report = {
        "schema_version": 1,
        "intended_use": "research",
        "split": split,
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "bundle_manifest": str(bundle_path),
        "bundle_manifest_sha256": hashlib.sha256(bundle_path.read_bytes()).hexdigest(),
        "model": bundle,
        **provenance,
        "device": str(selected_device),
        "precision": "float32",
        "threshold": effective_threshold,
        "threshold_source": "bundle_manifest" if threshold is None else "validation_override",
        "postprocessing": {
            "method": "BFS",
            "connectivity": 4,
            "min_region_pixels": min_region_pixels,
            "space": "original_size",
        },
        "baseline": baseline,
        "metric_definitions": {
            "aggregation": "per-image macro, at original image size",
            "double_empty_dice_iou": 1,
            "single_empty_dice_iou": 0,
            "undefined_precision_recall": "1 only for double-empty, otherwise 0",
            "empty_gt_false_positive_rate": "fraction of empty-GT images with foreground",
        },
        "metrics": {
            method: aggregate_metrics(row for row in rows if row["method"] == method)
            for method in ("unet_raw", "unet_bfs", "otsu")
        },
        "per_image": rows,
        "examples": examples,
        "overlay_panels": ["image", "ground truth (green)", "unet_raw", "unet_bfs", "otsu"],
        "limitations": "Research metrics only; no clinical confidence, diagnosis or qualification",
    }
    with (output / "per_image.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    write_json(output / "evaluation.json", report)
    LOGGER.info(
        "Evaluated %d %s images with threshold=%.4f from %s; Otsu polarity=%s (%s)",
        len(dataset),
        split,
        effective_threshold,
        report["threshold_source"],
        baseline["polarity"],
        baseline["polarity_selection"],
    )
    return report


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True, help="Exported bundle manifest.json")
    parser.add_argument(
        "--manifest", type=Path, required=True, help="Sealed training data manifest"
    )
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--split", choices=("val", "test"), default="test")
    parser.add_argument("--device", choices=("auto", "mps", "cpu"), default="auto")
    parser.add_argument("--threshold", type=float, help="Validation only; never override on test")
    parser.add_argument(
        "--min-region-pixels",
        type=int,
        default=16,
        help="Predeclared four-connected cleanup size in original-image pixels",
    )
    parser.add_argument(
        "--otsu-polarity",
        choices=("dark", "bright", "validation"),
        default="dark",
        help="Predeclared polarity (default dark), or choose using val Dice only",
    )
    parser.add_argument("--max-examples", type=int, default=8)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    evaluate_model(
        args.bundle,
        args.manifest,
        args.output_dir,
        split=args.split,
        device=args.device,
        threshold=args.threshold,
        min_region_pixels=args.min_region_pixels,
        otsu_polarity=args.otsu_polarity,
        max_examples=args.max_examples,
    )


if __name__ == "__main__":
    main()
