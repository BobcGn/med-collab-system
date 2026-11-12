from __future__ import annotations

import time
from pathlib import Path

import numpy as np

from app.core.config.settings import (
    Settings,
    effective_model_settings,
    validate_bundle_image_type,
)
from app.core.errors.exceptions import DependencyMissingError, InferenceUnavailableError
from app.domain.entities.model import ModelDescriptor
from app.inference.model_bundle import file_sha256, load_bundle
from app.schemas.internal.segmentation import BackendPrediction, PreparedModelInput


class TorchUNetEngine:
    """Owns the Torch model instance, weights loading, and inference execution."""

    def __init__(self, settings: Settings):
        self.settings = effective_model_settings(settings)

        try:
            import torch
        except ModuleNotFoundError as error:
            error_type = (
                InferenceUnavailableError
                if self.settings.bundle_manifest is not None
                else DependencyMissingError
            )
            raise error_type(
                message="Torch is required for the torch_unet backend",
                detail="Install the optional dependency group: pip install -e '.[unet]'",
            ) from error

        self.torch = torch
        self.device = self._resolve_device()
        self.weights_loaded = False
        if self.settings.manifest_path is not None:
            self._load_bundle()
        else:
            from app.inference.engines.unet_model import UNet2D

            self.model = UNet2D(
                in_channels=self.settings.in_channels,
                out_channels=self.settings.output_classes,
                base_channels=self.settings.base_channels,
            ).to(self.device)
            self._load_weights()
        self.model.eval()

    def describe_model(self) -> ModelDescriptor:
        manifest = self.settings.bundle_manifest
        return ModelDescriptor(
            name=self.settings.model_name,
            version=self.settings.model_version,
            backend=self.settings.inference_backend,
            device=str(self.device),
            input_height=self.settings.input_height,
            input_width=self.settings.input_width,
            in_channels=self.settings.in_channels,
            output_classes=self.settings.output_classes,
            weights_path=str(self.settings.weights_path) if self.settings.weights_path else None,
            weights_loaded=self.weights_loaded,
            intended_use=manifest["intended_use"] if manifest else "unspecified",
            bundle_manifest_sha256=self.settings.bundle_manifest_sha256,
            supported_image_types=list(manifest["model_config"]["supported_image_types"])
            if manifest
            else [],
        )

    def predict(self, prepared_input: PreparedModelInput) -> BackendPrediction:
        """Run the Torch model and return a probability map in model-space."""
        validate_bundle_image_type(self.settings, prepared_input.request.image_type)
        if self.settings.bundle_manifest is not None and prepared_input.tensor.shape != (
            self.settings.in_channels,
            self.settings.input_height,
            self.settings.input_width,
        ):
            raise InferenceUnavailableError(
                message="Prepared input shape does not match the configured model bundle"
            )
        tensor = self.torch.from_numpy(prepared_input.tensor).unsqueeze(0).to(self.device)
        self._synchronize_device()
        start = time.perf_counter()
        with self.torch.inference_mode():
            logits = self.model(tensor)
            if self.settings.output_classes == 1:
                probabilities = self.torch.sigmoid(logits[:, 0, :, :])
                class_index = 0
            else:
                probabilities = self.torch.softmax(logits, dim=1)[
                    :, self.settings.lesion_class_index, :, :
                ]
                class_index = self.settings.lesion_class_index
        self._synchronize_device()
        inference_ms = int((time.perf_counter() - start) * 1000)
        research = self.settings.bundle_manifest is not None
        return BackendPrediction(
            probability_map=probabilities.squeeze(0).cpu().numpy().astype(np.float32),
            class_index=class_index,
            class_name="target_region" if research else "lesion",
            label=(
                "ultrasound-target-region"
                if research
                else f"{prepared_input.request.image_type.lower()}-lesion"
            ),
            inference_ms=inference_ms,
            model_note=self._build_model_note(),
        )

    def _resolve_device(self):  # noqa: ANN202 - Torch runtime object is backend-specific.
        requested = self.settings.device.lower()
        if requested.startswith("cuda") and self.torch.cuda.is_available():
            return self.torch.device(requested)
        if (
            requested == "mps"
            and hasattr(self.torch.backends, "mps")
            and self.torch.backends.mps.is_available()
        ):
            return self.torch.device("mps")
        return self.torch.device("cpu")

    def _synchronize_device(self) -> None:
        if self.device.type == "mps":
            self.torch.mps.synchronize()
        elif self.device.type == "cuda":
            self.torch.cuda.synchronize(self.device)

    def _load_bundle(self) -> None:
        try:
            model, manifest = load_bundle(self.settings.manifest_path, device=str(self.device))
            digest = file_sha256(self.settings.manifest_path)
            if (
                manifest != self.settings.bundle_manifest
                or digest != self.settings.bundle_manifest_sha256
            ):
                raise ValueError("Model bundle manifest changed during initialization")
        except Exception as error:
            raise InferenceUnavailableError(
                message="Model bundle could not be loaded",
                detail=str(error),
            ) from error
        self.model = model
        self.weights_loaded = True

    def _load_weights(self) -> None:
        weights_path = self.settings.weights_path
        if weights_path is None:
            if not self.settings.allow_random_weights:
                raise InferenceUnavailableError(
                    message="Model weights are not configured",
                    detail="Set model.weights_path or enable allow_random_weights",
                )
            return

        if not Path(weights_path).exists():
            if not self.settings.allow_random_weights:
                raise InferenceUnavailableError(
                    message="Model weights file does not exist",
                    detail=str(weights_path),
                )
            return

        try:
            checkpoint = self.torch.load(weights_path, map_location=self.device, weights_only=True)
            state_dict = checkpoint
            if isinstance(checkpoint, dict):
                for candidate_key in ("state_dict", "model_state_dict"):
                    if candidate_key in checkpoint:
                        state_dict = checkpoint[candidate_key]
                        break
            self.model.load_state_dict(state_dict, strict=True)
        except Exception as error:
            raise InferenceUnavailableError(
                message="Model weights could not be loaded",
                detail=str(error),
            ) from error
        self.weights_loaded = True

    def _build_model_note(self) -> str:
        if self.settings.bundle_manifest is not None:
            return (
                "Research-only U-Net with verified bundle weights; not clinically qualified. "
                "Confidence is mean foreground probability, not calibrated confidence; "
                "no physical calibration or clinical risk assessment. "
                "Four-connected BFS cleanup in original-image pixels: "
                f"min_region_pixels={self.settings.min_region_pixels}"
            )
        if self.weights_loaded:
            return (
                "torch_unet backend executed a real PyTorch U-Net forward pass with loaded weights"
            )

        if self.settings.weights_path:
            return (
                "torch_unet backend executed a real PyTorch U-Net forward pass with randomly "
                "initialized weights because the configured checkpoint was unavailable"
            )

        return (
            "torch_unet backend executed a real PyTorch U-Net forward pass with randomly "
            "initialized weights because no checkpoint was configured"
        )
