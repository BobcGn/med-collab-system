from __future__ import annotations

import time

import numpy as np

from app.core.config.settings import Settings
from app.domain.entities.model import ModelDescriptor
from app.inference.backends.base import SegmentationBackend
from app.preprocessing.image_loader import ImageLoader
from app.preprocessing.image_preprocessor import ImagePreprocessor
from app.schemas.internal.segmentation import PreparedSegmentationInput, RawInferenceOutput


class SegmentationPipeline:
    """Own the end-to-end image decoding, model execution, and mask restoration."""

    def __init__(
        self,
        settings: Settings,
        backend: SegmentationBackend,
        image_loader: ImageLoader | None = None,
        image_preprocessor: ImagePreprocessor | None = None,
    ):
        self.settings = settings
        self.backend = backend
        self.image_loader = image_loader or ImageLoader()
        self.image_preprocessor = image_preprocessor or ImagePreprocessor(settings)

    def get_model_descriptor(self) -> ModelDescriptor:
        return self.backend.describe_model()

    def run(self, request: PreparedSegmentationInput) -> RawInferenceOutput:
        loaded_image = self.image_loader.load(request)
        prepared_input = self.image_preprocessor.prepare(request, loaded_image)
        prediction = self.backend.predict(prepared_input)

        start = time.perf_counter()
        restored_probability_map = self.image_preprocessor.restore_probability_map(
            prediction.probability_map,
            loaded_image,
        )
        binary_mask = (restored_probability_map >= self.settings.probability_threshold).astype(np.uint8)
        postprocess_ms = int((time.perf_counter() - start) * 1000)

        return RawInferenceOutput(
            request=request,
            loaded_image=loaded_image,
            probability_map=np.clip(restored_probability_map, 0.0, 1.0).astype(np.float32),
            binary_mask=binary_mask,
            class_index=prediction.class_index,
            class_name=prediction.class_name,
            label=prediction.label,
            preprocess_ms=prepared_input.preprocess_ms,
            inference_ms=prediction.inference_ms,
            postprocess_ms=postprocess_ms,
            model_note=prediction.model_note,
        )
