from __future__ import annotations

from app.core.config.settings import Settings
from app.core.errors.exceptions import UnsupportedImageSourceError
from app.schemas.internal.segmentation import PreparedSegmentationInput
from app.schemas.requests.segmentation import SegmentationRequest
from app.utils.ids import build_request_id

SUPPORTED_SOURCES = {"LOCAL_FILE", "DATA_URL", "OBJECT_STORAGE", "INLINE_REFERENCE"}


class RequestNormalizer:
    """Normalize API requests into a compact internal request object."""

    def __init__(self, settings: Settings):
        self.settings = settings

    def normalize(self, request: SegmentationRequest) -> PreparedSegmentationInput:
        if request.image.source_type not in SUPPORTED_SOURCES:
            raise UnsupportedImageSourceError(
                message="Unsupported image source type",
                detail=request.image.source_type,
            )

        return PreparedSegmentationInput(
            request_id=request.request_id or build_request_id(),
            hospital_id=request.hospital_id,
            patient_id=request.patient_id,
            patient_name=request.patient_name,
            image_type=request.image_type.upper(),
            source_type=request.image.source_type,
            image_reference=request.image.value,
            mime_type=request.image.mime_type,
            return_mask=request.options.return_mask,
            return_contour=request.options.return_contour,
            return_metrics=request.options.return_metrics,
        )
