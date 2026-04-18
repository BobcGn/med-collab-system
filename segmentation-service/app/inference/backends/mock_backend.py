from __future__ import annotations

import numpy as np

from app.core.config.settings import Settings
from app.domain.entities.model import ModelDescriptor
from app.inference.backends.base import SegmentationBackend
from app.schemas.internal.segmentation import BackendPrediction, PreparedModelInput


class MockSegmentationBackend(SegmentationBackend):
    """Deterministic fallback backend for API contract testing.

    The mock backend is still image-aware: it uses the resized grayscale image to
    synthesize a stable probability map instead of returning a fixed constant.
    This makes the rest of the pipeline behave like a real segmentation flow.
    """

    def __init__(self, settings: Settings):
        self.settings = settings

    def describe_model(self) -> ModelDescriptor:
        return ModelDescriptor(
            name=self.settings.model_name,
            version=self.settings.model_version,
            backend=self.settings.inference_backend,
            device=self.settings.device,
            input_height=self.settings.input_height,
            input_width=self.settings.input_width,
            in_channels=self.settings.in_channels,
            output_classes=self.settings.output_classes,
            weights_path=str(self.settings.weights_path) if self.settings.weights_path else None,
            weights_loaded=False,
        )

    def predict(self, prepared_input: PreparedModelInput) -> BackendPrediction:
        base_map = prepared_input.resized_image.astype(np.float32)
        normalized = base_map - float(base_map.min())
        dynamic_range = float(normalized.max())
        if dynamic_range > 0:
            normalized = normalized / dynamic_range

        probability_map = np.clip((normalized * 0.82) + 0.08, 0.0, 1.0)
        if float(probability_map.max()) < self.settings.probability_threshold:
            probability_map[prepared_input.model_height // 4 : prepared_input.model_height // 2,
                            prepared_input.model_width // 4 : prepared_input.model_width // 2] = 0.86

        return BackendPrediction(
            probability_map=probability_map,
            class_index=self.settings.lesion_class_index,
            class_name="lesion",
            label=f"{prepared_input.request.image_type.lower()}-candidate",
            inference_ms=5,
            model_note="mock backend generated a deterministic probability map",
        )

