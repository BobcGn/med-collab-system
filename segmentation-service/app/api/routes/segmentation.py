from fastapi import APIRouter, Depends

from app.api.dependencies.services import get_segmentation_orchestrator
from app.schemas.requests.segmentation import BatchSegmentationRequest, SegmentationRequest
from app.schemas.responses.segmentation import BatchSegmentationResponse, SegmentationResponse
from app.services.orchestration.segmentation import SegmentationOrchestrator

router = APIRouter(prefix="/segment", tags=["segmentation"])


@router.post("", response_model=SegmentationResponse)
def segment_image(
    request: SegmentationRequest,
    orchestrator: SegmentationOrchestrator = Depends(get_segmentation_orchestrator),
) -> SegmentationResponse:
    """Run segmentation for a single image request."""

    return orchestrator.run(request)


@router.post("/batch", response_model=BatchSegmentationResponse)
def segment_batch(
    request: BatchSegmentationRequest,
    orchestrator: SegmentationOrchestrator = Depends(get_segmentation_orchestrator),
) -> BatchSegmentationResponse:
    """Run segmentation sequentially for a small batch of requests."""

    return orchestrator.run_batch(request)
