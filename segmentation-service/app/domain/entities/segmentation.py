from __future__ import annotations

from dataclasses import dataclass, field

from app.domain.entities.model import ModelDescriptor
from app.domain.value_objects.geometry import BoundingBox, ContourPoint


@dataclass(frozen=True, slots=True)
class SegmentationRegion:
    """Single segmented region returned to downstream services."""

    region_id: str
    class_name: str
    label: str
    confidence: float
    location: str
    severity: str
    coverage_percent: float
    estimated_size_mm: float
    bounding_box: BoundingBox
    contour: list[ContourPoint] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class SegmentationTiming:
    """Execution timings split by pipeline stage."""

    preprocess_ms: int
    inference_ms: int
    postprocess_ms: int


@dataclass(frozen=True, slots=True)
class SegmentationArtifacts:
    """Paths to optional artifacts written for debugging and reporting."""

    mask_path: str | None = None
    overlay_path: str | None = None
    metadata_path: str | None = None


@dataclass(frozen=True, slots=True)
class QualityGateDecision:
    """Decision returned by the post-inference quality gate."""

    status: str
    reason: str


@dataclass(frozen=True, slots=True)
class SegmentationResult:
    """Complete structured result for one segmentation request."""

    request_id: str
    status: str
    message: str
    patient_id: str
    patient_name: str
    hospital_id: str
    image_type: str
    model: ModelDescriptor
    timing: SegmentationTiming
    quality_gate: QualityGateDecision
    artifacts: SegmentationArtifacts
    regions: list[SegmentationRegion] = field(default_factory=list)
