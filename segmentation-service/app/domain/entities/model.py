from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True, slots=True)
class ModelDescriptor:
    """Describe the currently active model backend and runtime characteristics."""

    name: str
    version: str
    backend: str
    device: str
    input_height: int
    input_width: int
    in_channels: int
    output_classes: int
    weights_path: str | None
    weights_loaded: bool
    intended_use: str = "unspecified"
    bundle_manifest_sha256: str | None = None
    supported_image_types: list[str] = field(default_factory=list)
