"""Tiny synthetic CPU runs test plumbing, NOT BUSI accuracy or clinical readiness."""

from __future__ import annotations

import csv
import json
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

pytest.importorskip("torch")

import torch

from app.inference.model_bundle import load_bundle
from app.training import train as training_module
from app.training.data import build_manifest
from app.training.evaluate import evaluate_model, otsu_mask, predict_probabilities
from app.training.train import (
    TrainConfig,
    _plot_history,
    resolve_device,
    train_model,
    validate_manifest,
)


@pytest.fixture
def synthetic_manifest(tmp_path: Path) -> Path:
    root = tmp_path / "synthetic"
    rng = np.random.default_rng(19)
    # Three distinct images per category give train/val/test one of each. These
    # invented samples carry no patient identities and no real-data assertions.
    for category in ("benign", "malignant", "normal"):
        directory = root / category
        directory.mkdir(parents=True)
        for index in range(3):
            image = rng.integers(80, 220, size=(35, 43), dtype=np.uint8)
            if category != "normal":
                mask = np.zeros_like(image)
                mask[8:23, 11 + index : 27 + index] = 255
                image[mask > 0] //= 3
                Image.fromarray(mask).save(directory / f"synthetic_{index}_mask.png")
            Image.fromarray(image).save(directory / f"synthetic_{index}.png")
    manifest_path = tmp_path / "manifest.json"
    build_manifest(
        root,
        manifest_path,
        seed=5,
        source_url="https://example.invalid/synthetic-test-only",
        license_name="Invented test pixels, no real patient data",
        source_verified=True,
    )
    return manifest_path


def test_tiny_cpu_training_export_and_original_size_holdout(
    synthetic_manifest: Path,
    tmp_path: Path,
) -> None:
    previous_threads = torch.get_num_threads()
    torch.set_num_threads(1)
    try:
        output = tmp_path / "training"
        run = train_model(
            TrainConfig(
                synthetic_manifest,
                output,
                epochs=1,
                batch_size=2,
                image_size=32,
                base_channels=8,
                device="cpu",
                seed=7,
                augment=True,
                plots=False,
            )
        )
        assert run["status"] == "completed"
        assert run["epochs_completed"] == run["best_epoch"] == 1
        assert run["precision"] == "float32"
        assert run["patient_independence_verified"] is False
        assert run["selection"]["split"] == "val"
        assert run["selection"]["space"] == "original_size"
        assert len(run["manifest_sha256"]) == 64
        assert run["split_counts"] == {"train": 3, "val": 3, "test": 3}
        for filename in ("run.json", "history.csv", "history.json", "checkpoints/last.pt"):
            assert (output / filename).is_file()
        with (output / "history.csv").open(newline="", encoding="utf-8") as stream:
            history = list(csv.DictReader(stream))
        assert len(history) == 1
        assert np.isfinite(float(history[0]["train_loss"]))
        assert 0 <= float(history[0]["val_dice"]) <= 1
        # This is our own trusted-local synthetic checkpoint, restricted loading.
        checkpoint = torch.load(
            output / "checkpoints/last.pt", weights_only=True, map_location="cpu"
        )
        assert checkpoint["epoch"] == 1
        assert checkpoint["optimizer_state_dict"]["state"]
        assert checkpoint["manifest_sha256"] == run["manifest_sha256"]
        bundle_path = Path(run["bundle_manifest"])
        model, bundle = load_bundle(bundle_path)
        assert bundle["model_config"]["base_channels"] == 8
        assert bundle["model_config"]["input_height"] == 32
        assert bundle["training"]["epoch"] == 1
        assert bundle["training"]["seed"] == 7
        assert all(parameter.dtype == torch.float32 for parameter in model.parameters())
        probabilities = predict_probabilities(
            model,
            np.full((35, 43), 0.5, dtype=np.float32),
            bundle["model_config"],
            torch.device("cpu"),
        )
        assert probabilities.shape == (35, 43)
        assert probabilities.dtype == np.float32
        report = evaluate_model(
            bundle_path,
            synthetic_manifest,
            tmp_path / "evaluation",
            device="cpu",
            min_region_pixels=3,
            otsu_polarity="validation",
            max_examples=2,
        )
        assert report["threshold_source"] == "bundle_manifest"
        assert report["threshold"] == bundle["threshold"]
        assert report["baseline"]["selection_split"] == "val"
        assert set(report["baseline"]["validation_macro_dice"]) == {"dark", "bright"}
        assert set(report["metrics"]) == {"unet_raw", "unet_bfs", "otsu"}
        for metrics in report["metrics"].values():
            assert metrics["image_count"] == 3
            assert metrics["positive_cohort"]["image_count"] == 2
            assert metrics["empty_gt_count"] == 1
            assert "accuracy" not in metrics
        assert len(report["per_image"]) == 9
        assert all(row["height"] == 35 and row["width"] == 43 for row in report["per_image"])
        assert len(report["examples"]) == 2
        for example in report["examples"]:
            assert (tmp_path / "evaluation" / example["path"]).is_file()
        with (tmp_path / "evaluation/per_image.csv").open(newline="", encoding="utf-8") as stream:
            assert len(list(csv.DictReader(stream))) == 9
        saved = json.loads((tmp_path / "evaluation/evaluation.json").read_text())
        assert saved["manifest_sha256"] == run["manifest_sha256"]
        assert saved["dataset_sha256"] == run["dataset_sha256"]
        validation_report = evaluate_model(
            bundle_path,
            synthetic_manifest,
            tmp_path / "validation",
            split="val",
            device="cpu",
            threshold=0.6,
            max_examples=0,
        )
        assert validation_report["threshold_source"] == "validation_override"
        assert validation_report["threshold"] == 0.6
        assert json.loads(bundle_path.read_text())["threshold"] == 0.5
        # Even a metadata-only edit invalidates the sealed split provenance.
        edited = json.loads(synthetic_manifest.read_text())
        edited["source"]["license"] += " (edited)"
        synthetic_manifest.write_text(json.dumps(edited))
        with pytest.raises(ValueError, match="training manifest_sha256"):
            evaluate_model(bundle_path, synthetic_manifest, tmp_path / "changed", device="cpu")
    finally:
        torch.set_num_threads(previous_threads)


def test_best_checkpoint_uses_only_val_dice_with_earliest_ties_and_patience(
    synthetic_manifest: Path,
    tmp_path: Path,
    monkeypatch,
) -> None:
    # Control-flow test with invented scores, not another model-accuracy claim.
    scores = iter((0.6, 0.8, 0.8, 0.7))

    def fake_train(model, loader, optimizer, criterion, device, threshold):
        assert loader.dataset.split == "train"
        return 1.0, 0.25

    def fake_validate(model, loader, dataset, criterion, device, threshold):
        assert dataset.split == loader.dataset.split == "val"
        return 1.0, {
            "macro_dice": next(scores),
            "macro_iou": 0.5,
            "macro_precision": 0.5,
            "macro_recall": 0.5,
            "positive_cohort": {"macro_dice": 0.5},
            "empty_gt_false_positive_rate": 1.0,
        }

    monkeypatch.setattr(training_module, "_train_epoch", fake_train)
    monkeypatch.setattr(training_module, "_validate_epoch", fake_validate)
    run = train_model(
        TrainConfig(
            synthetic_manifest,
            tmp_path / "early-stop",
            epochs=8,
            patience=2,
            image_size=32,
            base_channels=8,
            device="cpu",
            plots=False,
        )
    )
    assert run["epochs_completed"] == 4
    assert run["stopped_early"] is True
    assert run["best_epoch"] == 2
    manifest = json.loads(Path(run["bundle_manifest"]).read_text())
    assert manifest["training"]["epoch"] == 2
    assert manifest["training"]["validation_metrics"]["macro_dice"] == 0.8


def test_float_probabilities_are_not_quantized_before_restoring_original_size() -> None:
    class NearThresholdModel(torch.nn.Module):
        def forward(self, image):
            return torch.full_like(image, 0.0004)

    probability = predict_probabilities(
        NearThresholdModel(),
        np.zeros((37, 41), dtype=np.float32),
        {"input_height": 32, "input_width": 32, "in_channels": 1},
        torch.device("cpu"),
    )
    assert probability.dtype == np.float32
    assert probability.shape == (37, 41)
    assert (probability > 0.50005).all()
    assert (probability < 0.5002).all()


def test_test_threshold_override_is_rejected_before_loading_any_weights(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="validation-only"):
        evaluate_model(
            tmp_path / "absent.json",
            tmp_path / "absent-data.json",
            tmp_path,
            split="test",
            threshold=0.6,
        )


def test_holdout_group_overlap_and_renamed_image_copies_are_rejected(
    synthetic_manifest: Path,
) -> None:
    document = json.loads(synthetic_manifest.read_text())
    root = Path(document["data_root"])
    train = next(record for record in document["records"] if record["split"] == "train")
    test = next(record for record in document["records"] if record["split"] == "test")
    original_group = test["group_id"]
    test["group_id"] = train["group_id"]
    synthetic_manifest.write_text(json.dumps(document))
    with pytest.raises(ValueError, match="Group.*overlaps"):
        validate_manifest(synthetic_manifest, root)
    test["group_id"] = original_group
    synthetic_manifest.write_text(json.dumps(document))
    (root / test["image"]).write_bytes((root / train["image"]).read_bytes())
    with pytest.raises(ValueError, match="Image content overlaps"):
        validate_manifest(synthetic_manifest, root)


def test_asset_digest_detects_changed_masks_under_an_unchanged_manifest(
    synthetic_manifest: Path,
) -> None:
    document = json.loads(synthetic_manifest.read_text())
    root = Path(document["data_root"])
    before = validate_manifest(synthetic_manifest, root)
    record = next(record for record in document["records"] if record["masks"])
    path = root / record["masks"][0]
    with Image.open(path) as image:
        mask = np.asarray(image).copy()
    mask[0, 0] = 255 - mask[0, 0]
    Image.fromarray(mask).save(path)
    after = validate_manifest(synthetic_manifest, root)
    assert before["manifest_sha256"] == after["manifest_sha256"]
    assert before["dataset_sha256"] != after["dataset_sha256"]


def test_training_rejects_invalid_configuration_before_reading_data(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="epochs"):
        train_model(TrainConfig(tmp_path / "absent.json", tmp_path, epochs=0))
    with pytest.raises(ValueError, match="at least 32"):
        train_model(TrainConfig(tmp_path / "absent.json", tmp_path, image_size=16))


def test_auto_device_is_logged_and_unavailable_explicit_mps_fails(monkeypatch, caplog) -> None:
    monkeypatch.setattr(torch.backends.mps, "is_available", lambda: False)
    with caplog.at_level("INFO"):
        assert resolve_device("auto") == torch.device("cpu")
    assert "selected=cpu precision=float32" in caplog.text
    with pytest.raises(RuntimeError, match="explicitly requested"):
        resolve_device("mps")


def test_otsu_is_image_only_with_explicit_polarity_and_constant_image_behavior() -> None:
    gray = np.array([[0.1, 0.1], [0.9, 0.9]], dtype=np.float32)
    dark, threshold = otsu_mask(gray, "dark")
    bright, other_threshold = otsu_mask(gray, "bright")
    np.testing.assert_array_equal(dark, [[1, 1], [0, 0]])
    np.testing.assert_array_equal(bright, 1 - dark)
    assert threshold == other_threshold
    constant = np.full((3, 3), 0.5, dtype=np.float32)
    assert otsu_mask(constant, "dark")[0].all()
    assert not otsu_mask(constant, "bright")[0].any()


def test_curves_are_headless_and_optional(tmp_path: Path) -> None:
    pytest.importorskip("matplotlib")
    destination = tmp_path / "curves.png"
    _plot_history(
        [{"epoch": 1, "train_loss": 1.2, "val_loss": 1.1, "train_dice": 0.2, "val_dice": 0.3}],
        destination,
    )
    assert destination.is_file()
