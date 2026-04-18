from __future__ import annotations

from app.core.config.settings import Settings
from app.domain.entities.model import ModelDescriptor
from app.inference.backends.base import SegmentationBackend
from app.inference.engines.torch_unet_engine import TorchUNetEngine
from app.schemas.internal.segmentation import BackendPrediction, PreparedModelInput


class TorchUNetBackend(SegmentationBackend):
    """Backend that executes a real 2D U-Net with PyTorch."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.engine = TorchUNetEngine(settings)

    def describe_model(self) -> ModelDescriptor:
        return self.engine.describe_model()

    def predict(self, prepared_input: PreparedModelInput) -> BackendPrediction:
        return self.engine.predict(prepared_input)

