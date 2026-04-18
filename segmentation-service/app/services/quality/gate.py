from __future__ import annotations

from app.core.config.settings import Settings
from app.domain.entities.segmentation import QualityGateDecision, SegmentationRegion
from app.schemas.internal.segmentation import RawInferenceOutput


class QualityGateService:
    """Evaluate whether the segmentation result is usable for downstream reporting."""

    def __init__(self, settings: Settings):
        self.settings = settings

    def evaluate(
        self,
        inference_output: RawInferenceOutput,
        regions: list[SegmentationRegion],
    ) -> QualityGateDecision:
        if inference_output.binary_mask.sum() == 0:
            return QualityGateDecision(status="FAIL", reason="No foreground mask was produced")

        if not regions:
            return QualityGateDecision(status="FAIL", reason="All detected regions were filtered out")

        best_confidence = max(region.confidence for region in regions)
        max_coverage = max(region.coverage_percent for region in regions)

        if best_confidence < self.settings.min_confidence:
            return QualityGateDecision(
                status="LIMITED",
                reason="Segmentation confidence is below the configured production threshold",
            )

        if max_coverage > 80.0:
            return QualityGateDecision(
                status="LIMITED",
                reason="Foreground coverage is unusually large and should be reviewed manually",
            )

        return QualityGateDecision(status="PASS", reason="Segmentation passed the bootstrap quality gate")

