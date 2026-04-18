from __future__ import annotations

from dataclasses import replace

from app.core.config.settings import Settings
from app.domain.entities.model import ModelDescriptor
from app.domain.entities.segmentation import (
    SegmentationArtifacts,
    SegmentationResult,
    SegmentationTiming,
)
from app.postprocessing.assembler import SegmentationAssembler
from app.preprocessing.normalizer import RequestNormalizer
from app.schemas.requests.segmentation import BatchSegmentationRequest, SegmentationRequest
from app.schemas.responses.segmentation import (
    ArtifactPathsResponse,
    BatchSegmentationResponse,
    BoundingBoxResponse,
    ContourPointResponse,
    ModelDescriptorResponse,
    QualityGateResponse,
    SegmentationRegionResponse,
    SegmentationResponse,
    TimingResponse,
)
from app.services.quality.gate import QualityGateService
from app.services.storage.artifacts import ArtifactStorageService
from app.inference.pipelines.segmentation_pipeline import SegmentationPipeline


class SegmentationOrchestrator:
    """Coordinate request normalization, inference, post-processing, and artifact output."""

    def __init__(
        self,
        settings: Settings,
        normalizer: RequestNormalizer,
        pipeline: SegmentationPipeline,
        assembler: SegmentationAssembler,
        quality_gate: QualityGateService,
        artifact_storage: ArtifactStorageService,
    ):
        self.settings = settings
        self.normalizer = normalizer
        self.pipeline = pipeline
        self.assembler = assembler
        self.quality_gate = quality_gate
        self.artifact_storage = artifact_storage

    def get_current_model(self) -> ModelDescriptor:
        return self.pipeline.get_model_descriptor()

    def run(self, request: SegmentationRequest) -> SegmentationResponse:
        result = self._run_domain_result(request)
        return _to_response(result)

    def run_batch(self, request: BatchSegmentationRequest) -> BatchSegmentationResponse:
        return BatchSegmentationResponse(items=[self.run(item) for item in request.items])

    def _run_domain_result(self, request: SegmentationRequest) -> SegmentationResult:
        prepared_request = self.normalizer.normalize(request)
        inference_output = self.pipeline.run(prepared_request)
        regions = self.assembler.assemble(inference_output)
        quality_gate = self.quality_gate.evaluate(inference_output, regions)

        result = SegmentationResult(
            request_id=prepared_request.request_id,
            status=_map_result_status(quality_gate.status),
            message=_build_result_message(quality_gate.status, quality_gate.reason, inference_output.model_note),
            patient_id=prepared_request.patient_id,
            patient_name=prepared_request.patient_name,
            hospital_id=prepared_request.hospital_id,
            image_type=prepared_request.image_type,
            model=self.get_current_model(),
            timing=SegmentationTiming(
                preprocess_ms=inference_output.preprocess_ms,
                inference_ms=inference_output.inference_ms,
                postprocess_ms=inference_output.postprocess_ms,
            ),
            quality_gate=quality_gate,
            artifacts=SegmentationArtifacts(),
            regions=regions,
        )

        artifacts = self.artifact_storage.persist(result, inference_output)
        return replace(result, artifacts=artifacts)


def _map_result_status(quality_status: str) -> str:
    return {
        "PASS": "completed",
        "LIMITED": "limited",
        "FAIL": "failed",
    }.get(quality_status, "failed")


def _build_result_message(quality_status: str, reason: str, model_note: str | None) -> str:
    prefix = {
        "PASS": "Segmentation completed",
        "LIMITED": "Segmentation completed with limitations",
        "FAIL": "Segmentation failed quality gate",
    }.get(quality_status, "Segmentation finished")

    if model_note:
        return f"{prefix}: {reason}. {model_note}"
    return f"{prefix}: {reason}."


def _to_response(result: SegmentationResult) -> SegmentationResponse:
    return SegmentationResponse(
        request_id=result.request_id,
        status=result.status,
        message=result.message,
        hospital_id=result.hospital_id,
        patient_id=result.patient_id,
        patient_name=result.patient_name,
        image_type=result.image_type,
        model=ModelDescriptorResponse(
            name=result.model.name,
            version=result.model.version,
            backend=result.model.backend,
            device=result.model.device,
            input_height=result.model.input_height,
            input_width=result.model.input_width,
            in_channels=result.model.in_channels,
            output_classes=result.model.output_classes,
            weights_path=result.model.weights_path,
            weights_loaded=result.model.weights_loaded,
        ),
        timing=TimingResponse(
            preprocess_ms=result.timing.preprocess_ms,
            inference_ms=result.timing.inference_ms,
            postprocess_ms=result.timing.postprocess_ms,
        ),
        quality_gate=QualityGateResponse(
            status=result.quality_gate.status,
            reason=result.quality_gate.reason,
        ),
        artifacts=ArtifactPathsResponse(
            mask_path=result.artifacts.mask_path,
            overlay_path=result.artifacts.overlay_path,
            metadata_path=result.artifacts.metadata_path,
        ),
        regions=[
            SegmentationRegionResponse(
                region_id=region.region_id,
                class_name=region.class_name,
                label=region.label,
                confidence=region.confidence,
                location=region.location,
                severity=region.severity,
                coverage_percent=region.coverage_percent,
                estimated_size_mm=region.estimated_size_mm,
                bounding_box=BoundingBoxResponse(
                    left_percent=region.bounding_box.left_percent,
                    top_percent=region.bounding_box.top_percent,
                    width_percent=region.bounding_box.width_percent,
                    height_percent=region.bounding_box.height_percent,
                ),
                contour=[
                    ContourPointResponse(
                        x_percent=point.x_percent,
                        y_percent=point.y_percent,
                    )
                    for point in region.contour
                ],
            )
            for region in result.regions
        ],
    )
