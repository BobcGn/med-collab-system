from __future__ import annotations

import time

import numpy as np

from app.core.config.settings import Settings, effective_model_settings
from app.preprocessing.transforms import prepare_tensor, restore_probabilities
from app.schemas.internal.segmentation import (
    LoadedRasterImage,
    PreparedModelInput,
    PreparedSegmentationInput,
)


class ImagePreprocessor:
    """Use the same float32 normalization and geometry as offline evaluation."""

    def __init__(self, settings: Settings):
        self.settings = effective_model_settings(settings)

    def prepare(
        self,
        request: PreparedSegmentationInput,
        loaded_image: LoadedRasterImage,
    ) -> PreparedModelInput:
        start = time.perf_counter()
        tensor = prepare_tensor(
            loaded_image.grayscale,
            self.settings.input_height,
            self.settings.input_width,
            channels=self.settings.in_channels,
        )
        resized_array = (tensor[0] * 0.5) + 0.5

        preprocess_ms = int((time.perf_counter() - start) * 1000)
        return PreparedModelInput(
            request=request,
            loaded_image=loaded_image,
            resized_image=resized_array,
            tensor=tensor,
            model_height=self.settings.input_height,
            model_width=self.settings.input_width,
            preprocess_ms=preprocess_ms,
        )

    def restore_probability_map(
        self, probability_map: np.ndarray, loaded_image: LoadedRasterImage
    ) -> np.ndarray:
        """Restore float probabilities, never an 8-bit approximation."""
        return restore_probabilities(probability_map, loaded_image.height, loaded_image.width)
