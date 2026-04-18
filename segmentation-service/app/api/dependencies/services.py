from functools import lru_cache

from app.core.config.settings import Settings, get_settings
from app.inference.backends.factory import build_segmentation_backend
from app.inference.pipelines.segmentation_pipeline import SegmentationPipeline
from app.postprocessing.assembler import SegmentationAssembler
from app.preprocessing.normalizer import RequestNormalizer
from app.services.orchestration.segmentation import SegmentationOrchestrator
from app.services.quality.gate import QualityGateService
from app.services.storage.artifacts import ArtifactStorageService


@lru_cache(maxsize=1)
def get_segmentation_orchestrator() -> SegmentationOrchestrator:
    """Build a single orchestrator instance reused by FastAPI dependency injection."""

    settings = get_settings()
    normalizer = RequestNormalizer(settings)
    backend = build_segmentation_backend(settings)
    pipeline = SegmentationPipeline(settings, backend)
    assembler = SegmentationAssembler(settings)
    quality_gate = QualityGateService(settings)
    artifact_storage = ArtifactStorageService(settings)
    return SegmentationOrchestrator(
        settings=settings,
        normalizer=normalizer,
        pipeline=pipeline,
        assembler=assembler,
        quality_gate=quality_gate,
        artifact_storage=artifact_storage,
    )


def get_runtime_settings() -> Settings:
    """Expose the resolved settings as a dependency."""

    return get_settings()
