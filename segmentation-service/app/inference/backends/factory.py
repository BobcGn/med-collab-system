from __future__ import annotations

from app.core.config.settings import Settings
from app.core.errors.exceptions import ConfigurationError
from app.inference.backends.base import SegmentationBackend
from app.inference.backends.mock_backend import MockSegmentationBackend


def build_segmentation_backend(settings: Settings) -> SegmentationBackend:
    """Instantiate the configured backend lazily.

    Torch imports are intentionally delayed so that the service can still start in
    mock mode on machines that do not have Torch installed.
    """

    backend = settings.inference_backend.lower()

    if backend == "mock":
        return MockSegmentationBackend(settings)

    if backend in {"torch_unet", "unet", "torch"}:
        from app.inference.backends.torch_unet_backend import TorchUNetBackend

        return TorchUNetBackend(settings)

    raise ConfigurationError(
        message="Unsupported inference backend",
        detail=settings.inference_backend,
    )

