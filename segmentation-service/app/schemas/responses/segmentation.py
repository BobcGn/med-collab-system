from __future__ import annotations

from pydantic import BaseModel


class BoundingBoxResponse(BaseModel):
    left_percent: float
    top_percent: float
    width_percent: float
    height_percent: float


class ContourPointResponse(BaseModel):
    x_percent: float
    y_percent: float


class ModelDescriptorResponse(BaseModel):
    name: str
    version: str
    backend: str
    device: str
    input_height: int
    input_width: int
    in_channels: int
    output_classes: int
    weights_path: str | None = None
    weights_loaded: bool


class TimingResponse(BaseModel):
    preprocess_ms: int
    inference_ms: int
    postprocess_ms: int


class QualityGateResponse(BaseModel):
    status: str
    reason: str


class ArtifactPathsResponse(BaseModel):
    mask_path: str | None = None
    overlay_path: str | None = None
    metadata_path: str | None = None


class SegmentationRegionResponse(BaseModel):
    region_id: str
    class_name: str
    label: str
    confidence: float
    location: str
    severity: str
    coverage_percent: float
    estimated_size_mm: float
    bounding_box: BoundingBoxResponse
    contour: list[ContourPointResponse]


class SegmentationResponse(BaseModel):
    request_id: str
    status: str
    message: str
    hospital_id: str
    patient_id: str
    patient_name: str
    image_type: str
    model: ModelDescriptorResponse
    timing: TimingResponse
    quality_gate: QualityGateResponse
    artifacts: ArtifactPathsResponse
    regions: list[SegmentationRegionResponse]


class BatchSegmentationResponse(BaseModel):
    items: list[SegmentationResponse]
