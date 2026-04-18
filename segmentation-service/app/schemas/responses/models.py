from __future__ import annotations

from pydantic import BaseModel


class ModelInfoResponse(BaseModel):
    """Expose the currently active model configuration to callers."""

    service_name: str
    environment: str
    backend: str
    model_name: str
    model_version: str
    device: str
    input_height: int
    input_width: int
    in_channels: int
    output_classes: int
    weights_path: str | None = None
    weights_loaded: bool
