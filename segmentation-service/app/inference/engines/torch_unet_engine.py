from __future__ import annotations

import time
from pathlib import Path

import numpy as np

from app.core.config.settings import Settings
from app.core.errors.exceptions import DependencyMissingError, InferenceUnavailableError
from app.domain.entities.model import ModelDescriptor
from app.inference.engines.unet_model import UNet2D
from app.schemas.internal.segmentation import BackendPrediction, PreparedModelInput


class TorchUNetEngine:
    """Owns the Torch model instance, weights loading, and inference execution."""

    def __init__(self, settings: Settings):
        self.settings = settings

        try:
            import torch
        except ModuleNotFoundError as error:
            raise DependencyMissingError(
                message="Torch is required for the torch_unet backend",
                detail="Install the optional dependency group: pip install -e '.[unet]'",
            ) from error

        self.torch = torch
        self.device = self._resolve_device()
        self.model = UNet2D(
            in_channels=settings.in_channels,
            out_channels=settings.output_classes,
            base_channels=settings.base_channels,
        ).to(self.device)
        self.weights_loaded = False
        self._load_weights()
        self.model.eval()

    def describe_model(self) -> ModelDescriptor:
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
        )

    def predict(self, prepared_input: PreparedModelInput) -> BackendPrediction:
        """Run the Torch model and return a probability map in model-space."""

        tensor = self.torch.from_numpy(prepared_input.tensor).unsqueeze(0).to(self.device)
        start = time.perf_counter()
        with self.torch.inference_mode():
            logits = self.model(tensor)
            if self.settings.output_classes == 1:
                probabilities = self.torch.sigmoid(logits[:, 0, :, :])
                class_index = 0
            else:
                probabilities = self.torch.softmax(logits, dim=1)[:, self.settings.lesion_class_index, :, :]
                class_index = self.settings.lesion_class_index

        inference_ms = int((time.perf_counter() - start) * 1000)
        return BackendPrediction(
            probability_map=probabilities.squeeze(0).cpu().numpy().astype(np.float32),
            class_index=class_index,
            class_name="lesion",
            label=f"{prepared_input.request.image_type.lower()}-lesion",
            inference_ms=inference_ms,
            model_note=self._build_model_note(),
        )

    def _resolve_device(self):  # noqa: ANN202 - Torch runtime object is backend-specific.
        requested = self.settings.device.lower()
        if requested.startswith("cuda") and self.torch.cuda.is_available():
            return self.torch.device(requested)
        if requested == "mps" and hasattr(self.torch.backends, "mps") and self.torch.backends.mps.is_available():
            return self.torch.device("mps")
        return self.torch.device("cpu")

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

        checkpoint = self.torch.load(weights_path, map_location=self.device)
        state_dict = checkpoint
        if isinstance(checkpoint, dict):
            for candidate_key in ("state_dict", "model_state_dict"):
                if candidate_key in checkpoint:
                    state_dict = checkpoint[candidate_key]
                    break

        self.model.load_state_dict(state_dict)
        self.weights_loaded = True

    def _build_model_note(self) -> str:
        if self.weights_loaded:
            return "torch_unet backend executed a real PyTorch U-Net forward pass with loaded weights"

        if self.settings.weights_path:
            return (
                "torch_unet backend executed a real PyTorch U-Net forward pass with randomly "
                "initialized weights because the configured checkpoint was unavailable"
            )

        return (
            "torch_unet backend executed a real PyTorch U-Net forward pass with randomly "
            "initialized weights because no checkpoint was configured"
        )
