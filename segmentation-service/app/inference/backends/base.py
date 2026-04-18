from __future__ import annotations

from abc import ABC, abstractmethod

from app.domain.entities.model import ModelDescriptor
from app.schemas.internal.segmentation import BackendPrediction, PreparedModelInput


class SegmentationBackend(ABC):
    """Abstract backend contract implemented by mock and Torch backends."""

    @abstractmethod
    def describe_model(self) -> ModelDescriptor:
        """Return a stable description of the active backend and model."""

    @abstractmethod
    def predict(self, prepared_input: PreparedModelInput) -> BackendPrediction:
        """Run the backend against the prepared model tensor."""

