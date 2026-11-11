from __future__ import annotations

import json

import numpy as np
import pytest

from app.inference.model_bundle import export_bundle, load_bundle, read_bundle_manifest
from app.preprocessing.transforms import prepare_tensor, resize_mask, restore_probabilities


def test_float_probability_restore_does_not_quantize() -> None:
    probabilities = np.full((3, 5), 0.5005, dtype=np.float32)
    restored = restore_probabilities(probabilities, 7, 11)
    np.testing.assert_allclose(restored, 0.5005, atol=1e-6)
    assert restored.dtype == np.float32


def test_shared_transforms_have_expected_units() -> None:
    image = np.array([[0, 1], [1, 0]], dtype=np.float32)
    tensor = prepare_tensor(image, 2, 2)
    np.testing.assert_array_equal(tensor, (image * 2 - 1)[None])
    mask = resize_mask(image, 5, 7)
    assert set(np.unique(mask)) == {0, 1}
    with pytest.raises(ValueError, match="non-finite"):
        restore_probabilities(np.array([[np.nan]]), 2, 2)


@pytest.fixture
def bundle(tmp_path):
    torch = pytest.importorskip("torch")
    from app.inference.engines.unet_model import UNet2D

    torch.manual_seed(3)
    model = UNet2D(1, 1, 8).eval()
    path = export_bundle(
        model,
        tmp_path / "bundle",
        model_config={
            "in_channels": 1,
            "output_classes": 1,
            "base_channels": 8,
            "input_height": 32,
            "input_width": 32,
            "supported_image_types": ["ULTRASOUND"],
        },
        training={"fixture_only": True, "seed": 3},
    )
    return path, model


def test_bundle_round_trip_and_checksum(bundle) -> None:
    import torch

    path, original = bundle
    loaded, manifest = load_bundle(path)
    tensor = torch.zeros(1, 1, 32, 32)
    with torch.inference_mode():
        torch.testing.assert_close(original(tensor), loaded(tensor))
    assert manifest["intended_use"] == "research"
    weights = path.parent / manifest["weights"]["file"]
    weights.write_bytes(b"tampered")
    with pytest.raises(ValueError, match="checksum"):
        load_bundle(path)


def test_bundle_rejects_escape_and_preprocess_drift(bundle) -> None:
    path, _ = bundle
    manifest = json.loads(path.read_text())
    original = json.loads(path.read_text())
    manifest["weights"]["file"] = "../weights.pt"
    path.write_text(json.dumps(manifest))
    with pytest.raises(ValueError, match="local bundle filename"):
        read_bundle_manifest(path)
    original["preprocessing"]["mean"] = 0.0
    path.write_text(json.dumps(original))
    with pytest.raises(ValueError, match="preprocessing"):
        read_bundle_manifest(path)
