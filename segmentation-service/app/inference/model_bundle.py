from __future__ import annotations

import hashlib
import json
import math
import os
import tempfile
from pathlib import Path
from typing import Any

from app.preprocessing.transforms import PREPROCESSING_NAME

BUNDLE_SCHEMA_VERSION = 1
PREPROCESSING = {
    "name": PREPROCESSING_NAME,
    "input_resampling": "bilinear",
    "mask_resampling": "nearest",
    "mean": 0.5,
    "std": 0.5,
}


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_config(config: dict[str, Any]) -> None:
    for key in ("in_channels", "output_classes", "base_channels", "input_height", "input_width"):
        value = config.get(key)
        if not isinstance(value, int) or isinstance(value, bool):
            raise ValueError(f"Model config {key} must be an integer")
    if config["in_channels"] != 1 or config["output_classes"] != 1:
        raise ValueError("Research bundle supports single-channel binary segmentation only")
    if not 8 <= config["base_channels"] <= 128:
        raise ValueError("Model base_channels must be between 8 and 128")
    if not all(32 <= config[key] <= 4096 for key in ("input_height", "input_width")):
        raise ValueError("Model dimensions must be between 32 and 4096")
    if config.get("supported_image_types") != ["ULTRASOUND"]:
        raise ValueError("Coursework bundle must declare supported_image_types=['ULTRASOUND']")


def read_bundle_manifest(manifest_path: Path | str) -> dict[str, Any]:
    path = Path(manifest_path).resolve()
    with path.open(encoding="utf-8") as stream:
        manifest = json.load(stream)
    if not isinstance(manifest, dict) or manifest.get("schema_version") != BUNDLE_SCHEMA_VERSION:
        raise ValueError("Unsupported model bundle schema")
    if manifest.get("architecture") != "unet2d_v1":
        raise ValueError("Unsupported model architecture")
    if manifest.get("intended_use") != "research":
        raise ValueError("Coursework model must be marked for research use")
    if manifest.get("preprocessing") != PREPROCESSING:
        raise ValueError("Unsupported or inconsistent model preprocessing")
    config = manifest.get("model_config")
    if not isinstance(config, dict):
        raise ValueError("Model config is missing")
    _validate_config(config)
    threshold = manifest.get("threshold")
    if (
        not isinstance(threshold, (int, float))
        or isinstance(threshold, bool)
        or not math.isfinite(threshold)
        or not 0 < threshold < 1
    ):
        raise ValueError("Model threshold must be finite and in (0, 1)")
    weights = manifest.get("weights")
    if not isinstance(weights, dict):
        raise ValueError("Model weights metadata is missing")
    filename = weights.get("file")
    if not isinstance(filename, str) or not filename or Path(filename).name != filename:
        raise ValueError("Model weight file must be a local bundle filename")
    weights_path = (path.parent / filename).resolve()
    if not weights_path.is_relative_to(path.parent) or not weights_path.is_file():
        raise ValueError("Model weights are missing or outside the bundle")
    if file_sha256(weights_path) != weights.get("sha256"):
        raise ValueError("Model weights checksum mismatch")
    if not isinstance(manifest.get("training"), dict) or not manifest["training"]:
        raise ValueError("Training provenance is missing")
    return manifest


def export_bundle(
    model: Any,
    output_dir: Path,
    *,
    model_config: dict[str, Any],
    training: dict[str, Any],
    threshold: float = 0.5,
) -> Path:
    """Export state_dict and a checked manifest; this is not clinical qualification."""
    import torch

    _validate_config(model_config)
    if not training:
        raise ValueError("Training provenance is required")
    if not math.isfinite(threshold) or not 0 < threshold < 1:
        raise ValueError("Threshold must be in (0, 1)")
    # Reject non-JSON provenance before producing any model files.
    json.dumps(training, allow_nan=False)
    output_dir = Path(output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix="weights-", suffix=".pt", dir=output_dir)
    os.close(descriptor)
    temporary_path = Path(temporary)
    try:
        state = {key: value.detach().cpu() for key, value in model.state_dict().items()}
        torch.save(state, temporary)
        digest = file_sha256(temporary_path)
        filename = f"weights-{digest[:16]}.pt"
        temporary_path.replace(output_dir / filename)
    finally:
        temporary_path.unlink(missing_ok=True)
    manifest = {
        "schema_version": BUNDLE_SCHEMA_VERSION,
        "architecture": "unet2d_v1",
        "intended_use": "research",
        "model_config": model_config,
        "preprocessing": PREPROCESSING,
        "threshold": threshold,
        "weights": {"file": filename, "sha256": digest},
        "training": training,
    }
    manifest_path = output_dir / "manifest.json"
    descriptor, temporary = tempfile.mkstemp(prefix=".manifest-", dir=output_dir)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(manifest, stream, ensure_ascii=False, indent=2, allow_nan=False)
            stream.write("\n")
        Path(temporary).replace(manifest_path)
    finally:
        Path(temporary).unlink(missing_ok=True)
    read_bundle_manifest(manifest_path)
    return manifest_path


def load_bundle(manifest_path: Path | str, device: str = "cpu") -> tuple[Any, dict[str, Any]]:
    import torch

    from app.inference.engines.unet_model import UNet2D

    path = Path(manifest_path).resolve()
    manifest = read_bundle_manifest(path)
    config = manifest["model_config"]
    model = UNet2D(config["in_channels"], config["output_classes"], config["base_channels"])
    state = torch.load(
        path.parent / manifest["weights"]["file"], map_location="cpu", weights_only=True
    )
    model.load_state_dict(state, strict=True)
    model.to(device)
    model.eval()
    return model, manifest
