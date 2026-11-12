"""Synthetic, untrained U-Net fixtures prove contracts/parity, never model efficacy."""

from __future__ import annotations

import json
from dataclasses import replace
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.api.dependencies.services import get_segmentation_orchestrator
from app.core.config.settings import (
    ENV_FIELD_MAP,
    Settings,
    effective_model_settings,
    get_settings,
)
from app.core.errors.exceptions import InferenceUnavailableError, InvalidImageError
from app.inference.backends.factory import build_segmentation_backend
from app.inference.model_bundle import export_bundle, file_sha256, load_bundle
from app.inference.pipelines.segmentation_pipeline import SegmentationPipeline
from app.preprocessing.image_preprocessor import ImagePreprocessor
from app.schemas.internal.segmentation import BackendPrediction
from app.schemas.requests.segmentation import SegmentationRequest
from app.training.metrics import remove_small_components


@pytest.fixture
def bundle(tmp_path):
    torch = pytest.importorskip("torch")
    from app.inference.engines.unet_model import UNet2D

    model = UNet2D(1, 1, 8).eval()
    # A hand-wired skip path produces confined foreground without any training.
    # All other paths are zero. This is a geometry fixture, not learned weights.
    with torch.no_grad():
        for value in model.state_dict().values():
            value.zero_()
        for module in model.modules():
            if isinstance(module, torch.nn.BatchNorm2d):
                module.running_var.fill_(1)
                module.weight.fill_(1)
        for block in (model.input_block, model.up4.conv):
            block.block[0].weight[0, 0, 1, 1] = 1
            block.block[3].weight[0, 0, 1, 1] = 1
        model.output.weight[0, 0, 0, 0] = 6
        model.output.bias.fill_(-3)
    return export_bundle(
        model,
        tmp_path / "bundle",
        model_config={
            "in_channels": 1,
            "output_classes": 1,
            "base_channels": 8,
            "input_height": 32,
            "input_width": 48,
            "supported_image_types": ["ULTRASOUND"],
        },
        training={"fixture_only": True, "training_performed": False},
        threshold=0.613,
    )


@pytest.fixture
def runtime(bundle, tmp_path, monkeypatch):
    for variable in (*ENV_FIELD_MAP, "SEGMENTATION_SERVICE_CONFIG_FILE"):
        monkeypatch.delenv(variable, raising=False)
    monkeypatch.setenv("SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH", str(bundle))
    monkeypatch.setenv("SEGMENTATION_SERVICE_ARTIFACTS_DIR", str(tmp_path / "artifacts"))
    # The bundle must override a deliberately incompatible legacy configuration.
    monkeypatch.setenv("SEGMENTATION_SERVICE_INFERENCE_BACKEND", "mock")
    monkeypatch.setenv("SEGMENTATION_SERVICE_ALLOW_RANDOM_WEIGHTS", "true")
    monkeypatch.setenv("SEGMENTATION_SERVICE_INPUT_HEIGHT", "80")
    monkeypatch.setenv("SEGMENTATION_SERVICE_INPUT_WIDTH", "96")
    monkeypatch.setenv("SEGMENTATION_SERVICE_IN_CHANNELS", "3")
    monkeypatch.setenv("SEGMENTATION_SERVICE_OUTPUT_CLASSES", "2")
    monkeypatch.setenv("SEGMENTATION_SERVICE_BASE_CHANNELS", "16")
    monkeypatch.setenv("SEGMENTATION_SERVICE_PROBABILITY_THRESHOLD", "0.99")
    monkeypatch.setenv("SEGMENTATION_SERVICE_PIXEL_SPACING_MM", "5.0")
    get_settings.cache_clear()
    get_segmentation_orchestrator.cache_clear()
    from app.main import create_app

    with TestClient(create_app()) as client:
        yield client, tmp_path
    get_segmentation_orchestrator.cache_clear()
    get_settings.cache_clear()


def _request(tmp_path: Path, *, shape=(59, 83)) -> dict:
    y, x = np.indices(shape)
    pixels = ((y + x) % 31).astype(np.uint8)
    pixels[12:34, 18:44] = 230
    image_path = tmp_path / "sample.png"
    Image.fromarray(pixels).save(image_path)
    return {
        "request_id": "research-test",
        "hospital_id": "H-001",
        "patient_id": "P-001",
        "patient_name": "Synthetic",
        "image_type": "ULTRASOUND",
        "image": {"source_type": "LOCAL_FILE", "value": str(image_path)},
    }


def test_real_bundle_offline_api_original_size_parity_and_provenance(
    runtime, bundle, monkeypatch
) -> None:
    import torch

    from app.training.evaluate import predict_probabilities

    client, tmp_path = runtime
    orchestrator = get_segmentation_orchestrator()
    effective = orchestrator.settings
    assert effective.input_height == 32 and effective.input_width == 48
    assert effective.base_channels == 8 and effective.in_channels == effective.output_classes == 1
    assert effective.probability_threshold == 0.613
    assert effective.inference_backend == "torch_unet"
    assert effective.allow_random_weights is False
    assert effective.pixel_spacing_mm is None
    assert effective.min_region_pixels == 16
    assert effective_model_settings(effective) is effective
    for stage in (
        orchestrator.normalizer,
        orchestrator.pipeline,
        orchestrator.pipeline.backend,
        orchestrator.pipeline.backend.engine,
        orchestrator.pipeline.image_preprocessor,
        orchestrator.assembler,
        orchestrator.quality_gate,
        orchestrator.artifact_storage,
    ):
        assert stage.settings is effective

    request = _request(tmp_path)
    captured = []
    run_pipeline = orchestrator.pipeline.run

    def capture(prepared):
        output = run_pipeline(prepared)
        captured.append(output)
        return output

    monkeypatch.setattr(orchestrator.pipeline, "run", capture)
    response = client.post("/api/v1/segment", json=request)
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload["status"] == "completed"
    assert payload["quality_gate"]["status"] == "PASS"
    descriptor = payload["model"]
    assert descriptor["intended_use"] == "research"
    assert descriptor["supported_image_types"] == ["ULTRASOUND"]
    assert descriptor["bundle_manifest_sha256"] == file_sha256(bundle)
    assert descriptor["weights_loaded"] is True
    assert descriptor["name"] == "unet-coursework-research"
    assert descriptor["version"].startswith("research-")
    assert descriptor["input_height"] == 32 and descriptor["input_width"] == 48
    current = client.get("/api/v1/models/current")
    assert current.status_code == 200
    for key in (
        "intended_use", "bundle_manifest_sha256", "supported_image_types", "weights_loaded"
    ):
        assert current.json()[key] == descriptor[key]
    assert "not clinically qualified" in payload["message"]
    assert "min_region_pixels=16" in payload["message"]

    model, manifest = load_bundle(bundle)
    with Image.open(request["image"]["value"]) as image:
        gray = np.asarray(image.convert("L"), dtype=np.float32) / 255
    offline = predict_probabilities(model, gray, manifest["model_config"], torch.device("cpu"))
    raw_output = captured[0]
    np.testing.assert_array_equal(raw_output.probability_map, offline)
    assert offline.shape == gray.shape == (59, 83)
    # Ensure parity is not accidentally satisfied by a quantized/constant map.
    assert np.unique(offline).size > 10
    assert np.any(np.abs(offline * 255 - np.rint(offline * 255)) > 1e-3)
    expected = remove_small_components((offline >= manifest["threshold"]).astype(np.uint8), 16)
    assert 0 < expected.sum() < expected.size * 0.8
    np.testing.assert_array_equal(raw_output.binary_mask, expected)
    with Image.open(payload["artifacts"]["mask_path"]) as image:
        np.testing.assert_array_equal(np.asarray(image) // 255, expected)
    assert sum(region["pixel_count"] for region in payload["regions"]) == int(expected.sum())
    for region in payload["regions"]:
        assert region["estimated_size_mm"] is None
        assert region["severity"] == "not_assessed"
        assert region["class_name"] == "target_region"
        assert "image quadrant" in region["location"]
        assert region["width_px"] > 0 and region["height_px"] > 0
    metadata = json.loads(Path(payload["artifacts"]["metadata_path"]).read_text())
    for key in (
        "intended_use", "bundle_manifest_sha256", "supported_image_types", "weights_loaded"
    ):
        assert metadata["model"][key] == descriptor[key]
    assert metadata["regions"] == payload["regions"]


def test_direct_engine_backend_and_pipeline_derive_bundle_settings(bundle, tmp_path) -> None:
    from app.inference.engines.torch_unet_engine import TorchUNetEngine

    original = Settings(
        model_manifest_path=str(bundle),
        inference_backend="mock",
        input_height=64,
        input_width=80,
        base_channels=16,
        in_channels=3,
        output_classes=2,
    )
    engine = TorchUNetEngine(original)
    assert engine.weights_loaded
    assert engine.settings.input_height == 32 and engine.settings.input_width == 48
    assert engine.settings.base_channels == 8
    assert original.input_height == 64 and original.inference_backend == "mock"
    backend = build_segmentation_backend(original)
    pipeline = SegmentationPipeline(original, backend)
    request = _request(tmp_path)
    prepared = get_prepared_request(request)
    output = pipeline.run(prepared)
    assert output.probability_map.shape == (59, 83)
    assert output.class_name == "target_region"
    model_input = ImagePreprocessor(original).prepare(prepared, output.loaded_image)
    assert model_input.tensor.shape == (1, 32, 48)
    with pytest.raises(InvalidImageError, match="ULTRASOUND"):
        pipeline.run(replace(prepared, image_type="CT", image_reference="nonexistent"))
    with pytest.raises(InvalidImageError, match="ULTRASOUND"):
        engine.predict(replace(model_input, request=replace(prepared, image_type="MRI")))
    with pytest.raises(InferenceUnavailableError, match="input shape"):
        engine.predict(replace(model_input, tensor=np.zeros((3, 64, 80), dtype=np.float32)))


def test_direct_bundle_pipeline_rejects_a_mock_backend(bundle) -> None:
    from app.inference.backends.mock_backend import MockSegmentationBackend

    settings = Settings(model_manifest_path=str(bundle), inference_backend="mock")
    with pytest.raises(InferenceUnavailableError, match="Backend does not match"):
        SegmentationPipeline(settings, MockSegmentationBackend(settings))


def test_bundle_missing_torch_is_inference_unavailable(bundle, monkeypatch) -> None:
    import sys

    from app.inference.engines.torch_unet_engine import TorchUNetEngine

    effective = effective_model_settings(Settings(model_manifest_path=str(bundle)))
    monkeypatch.setitem(sys.modules, "torch", None)
    with pytest.raises(InferenceUnavailableError, match="Torch is required"):
        TorchUNetEngine(effective)


def get_prepared_request(request: dict):
    from app.preprocessing.normalizer import RequestNormalizer

    return RequestNormalizer(Settings()).normalize(SegmentationRequest.model_validate(request))


@pytest.mark.parametrize("minimum", [None, 9, 24])
def test_api_cleanup_matches_evaluation_geometry_and_nullable_mm(
    runtime, monkeypatch, minimum
) -> None:
    client, tmp_path = runtime
    if minimum is not None:
        monkeypatch.setenv("SEGMENTATION_SERVICE_MIN_REGION_PIXELS", str(minimum))
        get_settings.cache_clear()
        get_segmentation_orchestrator.cache_clear()
    orchestrator = get_segmentation_orchestrator()
    assert orchestrator.get_current_model().weights_loaded is True
    expected_minimum = minimum if minimum is not None else 16
    assert orchestrator.settings.min_region_pixels == expected_minimum
    probability = np.full((32, 48), 0.07, dtype=np.float32)
    probability[3:7, 3:7] = 0.9137  # Exactly 16 pixels.
    probability[7:10, 7:10] = 0.8213  # Nine pixels, only diagonally adjacent.

    def deterministic_prediction(prepared):
        assert prepared.tensor.shape == (1, 32, 48)
        return BackendPrediction(
            probability_map=probability.copy(),
            class_index=0,
            class_name="target_region",
            label="research-fixture",
            inference_ms=0,
            model_note="TEST ONLY: synthetic probabilities with a real loaded research descriptor",
        )

    monkeypatch.setattr(orchestrator.pipeline.backend, "predict", deterministic_prediction)
    response = client.post("/api/v1/segment", json=_request(tmp_path, shape=(32, 48)))
    assert response.status_code == 200, response.text
    payload = response.json()
    expected = remove_small_components((probability >= 0.613).astype(np.uint8), expected_minimum)
    with Image.open(payload["artifacts"]["mask_path"]) as image:
        np.testing.assert_array_equal(np.asarray(image) // 255, expected)
    assert sum(region["pixel_count"] for region in payload["regions"]) == expected.sum()
    if expected_minimum == 24:
        assert payload["status"] == "failed"  # Empty online results still require review.
        assert payload["quality_gate"]["status"] == "FAIL"
        assert payload["regions"] == []
    else:
        assert payload["status"] == "completed"
        assert payload["quality_gate"]["status"] == "PASS"
        first = payload["regions"][0]
        assert first["pixel_count"] == 16
        assert first["width_px"] == first["height_px"] == 4
        assert first["estimated_size_mm"] is None
        assert first["severity"] == "not_assessed"
        assert first["location"] == "upper-left image quadrant"
        assert first["confidence"] == pytest.approx(float(probability[3:7, 3:7].mean()), abs=5e-5)
        assert len(payload["regions"]) == (2 if expected_minimum == 9 else 1)


@pytest.mark.parametrize("image_type", ["CT", "MRI", "OTHER"])
def test_bundle_api_rejects_unsupported_modality_before_image_loading(runtime, image_type) -> None:
    client, tmp_path = runtime
    request = _request(tmp_path)
    request["image_type"] = image_type
    request["image"]["value"] = "nonexistent"
    response = client.post("/api/v1/segment", json=request)
    assert response.status_code == 400
    assert response.json()["error_code"] == "INVALID_IMAGE"
    assert "ULTRASOUND" in response.json()["message"]
    assert not (tmp_path / "artifacts").exists()


@pytest.mark.parametrize(
    "failure",
    ["missing_manifest", "missing_weights", "bad_hash", "schema", "architecture", "preprocessing",
     "modality", "config", "state_shape", "malformed"],
)
def test_bad_bundles_fail_closed_without_random_or_mock_fallback(runtime, bundle, failure) -> None:
    client, tmp_path = runtime
    manifest = json.loads(bundle.read_text())
    weights = bundle.parent / manifest["weights"]["file"]
    if failure == "missing_manifest":
        bundle.unlink()
    elif failure == "missing_weights":
        weights.unlink()
    elif failure == "bad_hash":
        weights.write_bytes(weights.read_bytes() + b"tampered")
    elif failure == "malformed":
        bundle.write_text("not json")
    else:
        if failure == "schema":
            manifest["schema_version"] = 99
        elif failure == "architecture":
            manifest["architecture"] = "unsupported"
        elif failure == "preprocessing":
            manifest["preprocessing"]["mean"] = 0
        elif failure == "modality":
            manifest["model_config"]["supported_image_types"] = ["CT"]
        elif failure == "config":
            manifest["model_config"]["output_classes"] = 2
        elif failure == "state_shape":
            manifest["model_config"]["base_channels"] = 16
        bundle.write_text(json.dumps(manifest))
    response = client.post("/api/v1/segment", json=_request(tmp_path))
    assert response.status_code == 503, response.text
    assert response.json()["error_code"] == "INFERENCE_UNAVAILABLE"
    assert not (tmp_path / "artifacts").exists()


def test_bundle_change_after_deriving_settings_is_rejected(bundle) -> None:
    from app.inference.engines.torch_unet_engine import TorchUNetEngine

    effective = effective_model_settings(Settings(model_manifest_path=str(bundle)))
    manifest = json.loads(bundle.read_text())
    manifest["threshold"] = 0.7
    bundle.write_text(json.dumps(manifest))
    with pytest.raises(InferenceUnavailableError, match="could not be loaded"):
        TorchUNetEngine(effective)


def test_return_mask_false_and_artifact_collision_are_honored(runtime) -> None:
    client, tmp_path = runtime
    request = _request(tmp_path)
    request["options"] = {"return_mask": False}
    response = client.post("/api/v1/segment", json=request)
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload["artifacts"]["mask_path"] is None
    assert not (tmp_path / "artifacts" / request["request_id"] / "mask.png").exists()
    metadata_path = Path(payload["artifacts"]["metadata_path"])
    original_metadata = metadata_path.read_bytes()
    collision = client.post("/api/v1/segment", json=request)
    assert collision.status_code == 500
    assert collision.json()["error_code"] == "ARTIFACT_STORAGE_ERROR"
    assert metadata_path.read_bytes() == original_metadata


@pytest.mark.parametrize("request_id", ["../escape", "/absolute", "..", "a/b", "a\\b", "a\n"])
def test_artifact_request_id_is_a_safe_single_component(runtime, request_id) -> None:
    client, tmp_path = runtime
    request = _request(tmp_path)
    request["request_id"] = request_id
    response = client.post("/api/v1/segment", json=request)
    assert response.status_code == 422
    assert not (tmp_path / "artifacts").exists()
