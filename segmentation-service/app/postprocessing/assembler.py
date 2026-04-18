from __future__ import annotations

from app.core.config.settings import Settings
from app.domain.entities.segmentation import SegmentationRegion
from app.domain.value_objects.geometry import BoundingBox, ContourPoint
from app.postprocessing.mask_ops import extract_region_computations
from app.schemas.internal.segmentation import RawInferenceOutput


class SegmentationAssembler:
    """Transform masks and probabilities into API-facing region objects."""

    def __init__(self, settings: Settings):
        self.settings = settings

    def assemble(self, inference_output: RawInferenceOutput) -> list[SegmentationRegion]:
        computations = extract_region_computations(
            request_id=inference_output.request.request_id,
            probability_map=inference_output.probability_map,
            binary_mask=inference_output.binary_mask,
            min_region_pixels=self.settings.min_region_pixels,
            pixel_spacing_mm=self.settings.pixel_spacing_mm,
            max_contour_points=self.settings.max_contour_points,
        )

        regions: list[SegmentationRegion] = []
        for computation in computations:
            regions.append(
                SegmentationRegion(
                    region_id=computation.region_id,
                    class_name=inference_output.class_name,
                    label=inference_output.label,
                    confidence=round(computation.mean_confidence, 4),
                    location=_describe_location(computation.center_x_percent, computation.center_y_percent),
                    severity=_describe_severity(computation.mean_confidence, computation.coverage_percent),
                    coverage_percent=round(computation.coverage_percent, 4),
                    estimated_size_mm=round(computation.estimated_size_mm, 2),
                    bounding_box=BoundingBox(
                        left_percent=computation.left_percent,
                        top_percent=computation.top_percent,
                        width_percent=computation.width_percent,
                        height_percent=computation.height_percent,
                    ),
                    contour=[
                        ContourPoint(x_percent=point_x, y_percent=point_y)
                        for point_x, point_y in computation.contour
                    ]
                    if inference_output.request.return_contour
                    else [],
                )
            )

        return regions


def _describe_location(center_x_percent: float, center_y_percent: float) -> str:
    horizontal = "left" if center_x_percent < 38 else "right" if center_x_percent > 62 else "central"
    vertical = "upper" if center_y_percent < 38 else "lower" if center_y_percent > 62 else "mid"

    if horizontal == "central" and vertical == "mid":
        return "central region"
    if horizontal == "central":
        return f"{vertical} central region"
    if vertical == "mid":
        return f"{horizontal} middle region"
    return f"{vertical}-{horizontal} quadrant"


def _describe_severity(confidence: float, coverage_percent: float) -> str:
    if confidence >= 0.85 or coverage_percent >= 15.0:
        return "high"
    if confidence >= 0.65 or coverage_percent >= 6.0:
        return "medium"
    return "low"

