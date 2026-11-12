from __future__ import annotations

import numpy as np
import pytest

from app.core.config.settings import Settings
from app.postprocessing.mask_ops import extract_region_computations, remove_small_components
from app.preprocessing.image_preprocessor import ImagePreprocessor
from app.preprocessing.transforms import prepare_tensor
from app.schemas.internal.segmentation import LoadedRasterImage, PreparedSegmentationInput
from app.schemas.responses.segmentation import ModelDescriptorResponse, SegmentationRegionResponse
from app.training import metrics
from app.training.metrics import remove_small_components as evaluation_cleanup


def test_evaluation_reexports_the_service_bfs_and_uses_four_connectivity() -> None:
    assert set(metrics.__all__) == {
        "aggregate_metrics", "binary_metrics", "remove_small_components",
    }
    assert evaluation_cleanup is metrics.remove_small_components is remove_small_components
    mask = np.zeros((8, 8), dtype=np.uint8)
    mask[1:3, 1:3] = 1
    mask[3:5, 3:5] = 1
    original = mask.copy()
    assert not remove_small_components(mask, 5).any()  # Diagonals do not join.
    np.testing.assert_array_equal(remove_small_components(mask, 4), original)
    np.testing.assert_array_equal(mask, original)
    regions = extract_region_computations(
        "pixels",
        np.full(mask.shape, 0.8123, dtype=np.float32),
        mask,
        min_region_pixels=4,
        pixel_spacing_mm=None,
        max_contour_points=8,
    )
    assert len(regions) == 2
    for region in regions:
        assert region.estimated_size_mm is None
        assert region.pixel_count == 4
        assert region.width_px == region.height_px == 2
        assert region.mean_confidence == pytest.approx(0.8123)


@pytest.mark.parametrize("mask", [np.full((2, 2), 0.7), np.zeros((0, 2)), np.zeros((1, 2, 3))])
def test_shared_cleanup_rejects_probabilities_and_non_rasters(mask) -> None:
    with pytest.raises(ValueError):
        remove_small_components(mask, 2)


def test_service_preprocessing_and_restore_preserve_shared_float_geometry() -> None:
    gray = np.linspace(0, 1, 35, dtype=np.float32).reshape(5, 7)
    loaded = LoadedRasterImage(
        grayscale=gray,
        preview_rgb=np.zeros((5, 7, 3), dtype=np.uint8),
        width=7,
        height=5,
        source_type="LOCAL_FILE",
        reference="fixture",
    )
    request = PreparedSegmentationInput(
        request_id="fixture",
        hospital_id="H",
        patient_id="P",
        patient_name="Synthetic",
        image_type="CT",
        source_type="LOCAL_FILE",
        image_reference="fixture",
    )
    preprocessor = ImagePreprocessor(Settings(input_height=32, input_width=48, in_channels=3))
    prepared = preprocessor.prepare(request, loaded)
    np.testing.assert_array_equal(prepared.tensor, prepare_tensor(gray, 32, 48, channels=3))
    assert prepared.tensor.dtype == np.float32
    restored = preprocessor.restore_probability_map(
        np.full((32, 48), 0.5005, dtype=np.float32), loaded
    )
    np.testing.assert_allclose(restored, 0.5005, atol=1e-7)
    assert restored.dtype == np.float32 and restored.shape == (5, 7)
    assert (restored >= 0.5004).all()


def test_legacy_response_contract_accepts_old_descriptor_and_measurements() -> None:
    model = ModelDescriptorResponse(
        name="legacy",
        version="1",
        backend="mock",
        device="cpu",
        input_height=32,
        input_width=32,
        in_channels=1,
        output_classes=1,
        weights_loaded=False,
    )
    assert model.intended_use == "unspecified"
    assert model.bundle_manifest_sha256 is None
    assert model.supported_image_types == []
    region = SegmentationRegionResponse.model_validate({
        "region_id": "legacy-region-1",
        "class_name": "lesion",
        "label": "candidate",
        "confidence": 0.8,
        "location": "central region",
        "severity": "high",
        "coverage_percent": 20,
        "estimated_size_mm": 7.7,
        "bounding_box": {
            "left_percent": 10,
            "top_percent": 10,
            "width_percent": 20,
            "height_percent": 20,
        },
        "contour": [],
    })
    assert region.estimated_size_mm == 7.7
    assert region.pixel_count == region.width_px == region.height_px == 0
