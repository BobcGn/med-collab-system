from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator


class ImageReferenceRequest(BaseModel):
    source_type: Literal["LOCAL_FILE", "DATA_URL", "OBJECT_STORAGE", "INLINE_REFERENCE"]
    value: str = Field(min_length=1)
    mime_type: str | None = None

    @field_validator("value")
    @classmethod
    def validate_value(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("image.value cannot be blank")
        return normalized


class SegmentationOptionsRequest(BaseModel):
    return_mask: bool = True
    return_contour: bool = True
    return_metrics: bool = True


class SegmentationRequest(BaseModel):
    """Single image segmentation request."""

    request_id: str | None = None
    hospital_id: str = Field(min_length=1)
    patient_id: str = Field(min_length=1)
    patient_name: str = Field(min_length=1)
    image_type: str = Field(min_length=1)
    image: ImageReferenceRequest
    options: SegmentationOptionsRequest = Field(default_factory=SegmentationOptionsRequest)

    @field_validator("hospital_id", "patient_id", "patient_name", "image_type")
    @classmethod
    def validate_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("value cannot be blank")
        return normalized


class BatchSegmentationRequest(BaseModel):
    """Batch wrapper used for simple sequential bulk inference."""

    items: list[SegmentationRequest] = Field(min_length=1)
