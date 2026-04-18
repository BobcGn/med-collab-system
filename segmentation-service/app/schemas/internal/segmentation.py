from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np


@dataclass(slots=True)
class PreparedSegmentationInput:
    """Validated request metadata shared across the whole pipeline."""

    request_id: str
    hospital_id: str
    patient_id: str
    patient_name: str
    image_type: str
    source_type: str
    image_reference: str
    mime_type: str | None = None
    return_mask: bool = True
    return_contour: bool = True
    return_metrics: bool = True


@dataclass(slots=True)
class LoadedRasterImage:
    """Raw decoded image data in both grayscale and RGB forms."""

    grayscale: np.ndarray
    preview_rgb: np.ndarray
    width: int
    height: int
    source_type: str
    reference: str
    mime_type: str | None = None


@dataclass(slots=True)
class PreparedModelInput:
    """Tensor-ready image together with the original image metadata."""

    request: PreparedSegmentationInput
    loaded_image: LoadedRasterImage
    resized_image: np.ndarray
    tensor: np.ndarray
    model_height: int
    model_width: int
    preprocess_ms: int


@dataclass(slots=True)
class BackendPrediction:
    """Output produced directly by a backend before post-processing."""

    probability_map: np.ndarray
    class_index: int
    class_name: str
    label: str
    inference_ms: int
    model_note: str | None = None


@dataclass(slots=True)
class RawInferenceOutput:
    """Pipeline result after restoring the probability map to original geometry."""

    request: PreparedSegmentationInput
    loaded_image: LoadedRasterImage
    probability_map: np.ndarray
    binary_mask: np.ndarray
    class_index: int
    class_name: str
    label: str
    preprocess_ms: int
    inference_ms: int
    postprocess_ms: int
    model_note: str | None = None


@dataclass(slots=True)
class RegionComputation:
    """Intermediate component information extracted from a binary mask."""

    region_id: str
    pixel_count: int
    mean_confidence: float
    coverage_percent: float
    estimated_size_mm: float
    left_percent: float
    top_percent: float
    width_percent: float
    height_percent: float
    center_x_percent: float
    center_y_percent: float
    contour: list[tuple[float, float]] = field(default_factory=list)
