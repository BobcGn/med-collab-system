from __future__ import annotations

import time

import numpy as np

from app.core.config.settings import (
    Settings,
    effective_model_settings,
    validate_bundle_image_type,
)
from app.core.errors.exceptions import InferenceUnavailableError
from app.domain.entities.model import ModelDescriptor
from app.inference.backends.base import SegmentationBackend
from app.postprocessing.mask_ops import remove_small_components
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
        self.settings = effective_model_settings(settings)
        self.backend = backend
        self.image_loader = image_loader or ImageLoader()
        self.image_preprocessor = image_preprocessor or ImagePreprocessor(self.settings)
        if self.settings.bundle_manifest is not None:
            descriptor = self.backend.describe_model()
            if (
                not descriptor.weights_loaded
                or descriptor.backend != "torch_unet"
                or descriptor.intended_use != "research"
                or descriptor.bundle_manifest_sha256 != self.settings.bundle_manifest_sha256
                or descriptor.supported_image_types != ["ULTRASOUND"]
                or (descriptor.input_height, descriptor.input_width)
                != (self.settings.input_height, self.settings.input_width)
                or (descriptor.in_channels, descriptor.output_classes)
                != (self.settings.in_channels, self.settings.output_classes)
            ):
                raise InferenceUnavailableError(
                    message="Backend does not match the configured model bundle"
                )

    def get_model_descriptor(self) -> ModelDescriptor:
        return self.backend.describe_model()

    def run(self, request: PreparedSegmentationInput) -> RawInferenceOutput:
        validate_bundle_image_type(self.settings, request.image_type)
        loaded_image = self.image_loader.load(request)
        prepared_input = self.image_preprocessor.prepare(request, loaded_image)
        prediction = self.backend.predict(prepared_input)

        start = time.perf_counter()
        restored_probability_map = self.image_preprocessor.restore_probability_map(
            prediction.probability_map,
            loaded_image,
        )
        binary_mask = remove_small_components(
            (restored_probability_map >= self.settings.probability_threshold).astype(np.uint8),
            self.settings.min_region_pixels,
        )
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
