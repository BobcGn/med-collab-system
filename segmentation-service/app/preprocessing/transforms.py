from __future__ import annotations

import numpy as np
from PIL import Image

PREPROCESSING_NAME = "grayscale_minus1_plus1_v1"


def _raster(value: np.ndarray) -> np.ndarray:
    array = np.asarray(value, dtype=np.float32)
    if array.ndim != 2 or min(array.shape) == 0:
        raise ValueError("Expected a nonempty two-dimensional raster")
    if not np.isfinite(array).all():
        raise ValueError("Raster contains non-finite values")
    return array


def _dimensions(height: int, width: int) -> tuple[int, int]:
    if height < 1 or width < 1:
        raise ValueError("Raster dimensions must be positive")
    return width, height


def prepare_tensor(
    grayscale: np.ndarray, height: int, width: int, channels: int = 1
) -> np.ndarray:
    """Convert a grayscale [0, 1] raster to normalized float32 CHW model input."""
    array = _raster(grayscale)
    if channels < 1:
        raise ValueError("Channel count must be positive")
    if array.min() < 0 or array.max() > 1:
        raise ValueError("Grayscale input must be in [0, 1]")
    resized = Image.fromarray(array).resize(
        _dimensions(height, width), resample=Image.Resampling.BILINEAR
    )
    normalized = (np.asarray(resized, dtype=np.float32) - 0.5) / 0.5
    return np.ascontiguousarray(np.repeat(normalized[None, :, :], channels, axis=0))


def resize_mask(mask: np.ndarray, height: int, width: int) -> np.ndarray:
    array = _raster(mask)
    binary = (array > 0).astype(np.uint8)
    resized = Image.fromarray(binary).resize(
        _dimensions(height, width), resample=Image.Resampling.NEAREST
    )
    return np.asarray(resized, dtype=np.uint8).copy()


def restore_probabilities(
    probability_map: np.ndarray, height: int, width: int
) -> np.ndarray:
    array = _raster(probability_map)
    if array.min() < 0 or array.max() > 1:
        raise ValueError("Probabilities must be in [0, 1]")
    resized = Image.fromarray(array).resize(
        _dimensions(height, width), resample=Image.Resampling.BILINEAR
    )
    return np.clip(np.asarray(resized, dtype=np.float32), 0.0, 1.0).copy()
