from __future__ import annotations

import time

import numpy as np
from PIL import Image

from app.core.config.settings import Settings
from app.schemas.internal.segmentation import LoadedRasterImage, PreparedModelInput, PreparedSegmentationInput


class ImagePreprocessor:
    """Resize and normalize images into tensors expected by the model backend."""

    def __init__(self, settings: Settings):
        self.settings = settings

    def prepare(
        self,
        request: PreparedSegmentationInput,
        loaded_image: LoadedRasterImage,
    ) -> PreparedModelInput:
        start = time.perf_counter()

        pil_image = Image.fromarray(np.uint8(np.clip(loaded_image.grayscale, 0.0, 1.0) * 255.0), mode="L")
        resized_image = pil_image.resize(
            (self.settings.input_width, self.settings.input_height),
            resample=Image.Resampling.BILINEAR,
        )
        resized_array = np.asarray(resized_image, dtype=np.float32) / 255.0
        normalized = (resized_array - 0.5) / 0.5
        tensor = np.expand_dims(normalized, axis=0)

        if self.settings.in_channels > 1:
            tensor = np.repeat(tensor, self.settings.in_channels, axis=0)

        preprocess_ms = int((time.perf_counter() - start) * 1000)
        return PreparedModelInput(
            request=request,
            loaded_image=loaded_image,
            resized_image=resized_array,
            tensor=tensor.astype(np.float32),
            model_height=self.settings.input_height,
            model_width=self.settings.input_width,
            preprocess_ms=preprocess_ms,
        )

    def restore_probability_map(self, probability_map: np.ndarray, loaded_image: LoadedRasterImage) -> np.ndarray:
        """Resize model-space probabilities back to original image size."""

        pil_map = Image.fromarray(np.uint8(np.clip(probability_map, 0.0, 1.0) * 255.0), mode="L")
        restored = pil_map.resize(
            (loaded_image.width, loaded_image.height),
            resample=Image.Resampling.BILINEAR,
        )
        return np.asarray(restored, dtype=np.float32) / 255.0

