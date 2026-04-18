from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image

from app.core.config.settings import Settings
from app.core.errors.exceptions import ArtifactStorageError
from app.domain.entities.segmentation import SegmentationArtifacts, SegmentationResult
from app.schemas.internal.segmentation import RawInferenceOutput


class ArtifactStorageService:
    """Persist mask, overlay, and metadata artifacts for traceability."""

    def __init__(self, settings: Settings):
        self.settings = settings

    def persist(
        self,
        result: SegmentationResult,
        inference_output: RawInferenceOutput,
    ) -> SegmentationArtifacts:
        if not self.settings.return_artifacts:
            return SegmentationArtifacts()

        task_directory = self.settings.artifacts_path / result.request_id

        try:
            task_directory.mkdir(parents=True, exist_ok=True)
            mask_path = task_directory / "mask.png"
            overlay_path = task_directory / "overlay.png"
            metadata_path = task_directory / "result.json"

            self._save_mask(mask_path, inference_output.binary_mask)
            self._save_overlay(overlay_path, inference_output.loaded_image.preview_rgb, inference_output.binary_mask)
            metadata_path.write_text(
                json.dumps(_build_metadata_payload(result), indent=2, ensure_ascii=True),
                encoding="utf-8",
            )
        except Exception as error:  # noqa: BLE001 - wrap into a domain-specific API error.
            raise ArtifactStorageError(
                message="Failed to persist segmentation artifacts",
                detail=str(error),
            ) from error

        return SegmentationArtifacts(
            mask_path=str(mask_path),
            overlay_path=str(overlay_path),
            metadata_path=str(metadata_path),
        )

    def _save_mask(self, path: Path, binary_mask: np.ndarray) -> None:
        mask_pixels = np.uint8(np.clip(binary_mask, 0, 1) * 255)
        Image.fromarray(mask_pixels, mode="L").save(path)

    def _save_overlay(self, path: Path, preview_rgb: np.ndarray, binary_mask: np.ndarray) -> None:
        overlay = preview_rgb.copy()
        foreground = binary_mask.astype(bool)
        overlay[foreground] = np.array([255, 64, 64], dtype=np.uint8)
        blended = ((preview_rgb.astype(np.float32) * 0.55) + (overlay.astype(np.float32) * 0.45)).astype(np.uint8)
        Image.fromarray(blended, mode="RGB").save(path)


def _build_metadata_payload(result: SegmentationResult) -> dict[str, object]:
    return {
        "request_id": result.request_id,
        "status": result.status,
        "message": result.message,
        "hospital_id": result.hospital_id,
        "patient_id": result.patient_id,
        "patient_name": result.patient_name,
        "image_type": result.image_type,
        "model": {
            "name": result.model.name,
            "version": result.model.version,
            "backend": result.model.backend,
            "device": result.model.device,
            "weights_path": result.model.weights_path,
            "weights_loaded": result.model.weights_loaded,
        },
        "timing": {
            "preprocess_ms": result.timing.preprocess_ms,
            "inference_ms": result.timing.inference_ms,
            "postprocess_ms": result.timing.postprocess_ms,
        },
        "quality_gate": {
            "status": result.quality_gate.status,
            "reason": result.quality_gate.reason,
        },
        "regions": [
            {
                "region_id": region.region_id,
                "class_name": region.class_name,
                "label": region.label,
                "confidence": region.confidence,
                "location": region.location,
                "severity": region.severity,
                "coverage_percent": region.coverage_percent,
                "estimated_size_mm": region.estimated_size_mm,
                "bounding_box": {
                    "left_percent": region.bounding_box.left_percent,
                    "top_percent": region.bounding_box.top_percent,
                    "width_percent": region.bounding_box.width_percent,
                    "height_percent": region.bounding_box.height_percent,
                },
                "contour": [
                    {"x_percent": point.x_percent, "y_percent": point.y_percent}
                    for point in region.contour
                ],
            }
            for region in result.regions
        ],
    }

