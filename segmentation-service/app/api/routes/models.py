from fastapi import APIRouter, Depends

from app.api.dependencies.services import get_segmentation_orchestrator
from app.schemas.responses.models import ModelInfoResponse
from app.services.orchestration.segmentation import SegmentationOrchestrator

router = APIRouter(prefix="/models", tags=["models"])


@router.get("/current", response_model=ModelInfoResponse)
def current_model(
    orchestrator: SegmentationOrchestrator = Depends(get_segmentation_orchestrator),
) -> ModelInfoResponse:
    """Return the active backend and U-Net runtime configuration."""

    descriptor = orchestrator.get_current_model()
    return ModelInfoResponse(
        service_name=orchestrator.settings.app_name,
        environment=orchestrator.settings.environment,
        backend=descriptor.backend,
        model_name=descriptor.name,
        model_version=descriptor.version,
        device=descriptor.device,
        input_height=descriptor.input_height,
        input_width=descriptor.input_width,
        in_channels=descriptor.in_channels,
        output_classes=descriptor.output_classes,
        weights_path=descriptor.weights_path,
        weights_loaded=descriptor.weights_loaded,
    )
