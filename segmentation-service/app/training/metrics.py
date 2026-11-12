"""Image-level binary segmentation metrics (not clinical confidence or accuracy)."""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from typing import Any

import numpy as np

from app.postprocessing.mask_ops import remove_small_components as remove_small_components


def _binary_mask(value: np.ndarray) -> np.ndarray:
    mask = np.asarray(value)
    if mask.ndim != 2 or mask.size == 0:
        raise ValueError("A binary mask must be a nonempty HW array")
    if not np.isin(mask, (0, 1)).all():
        raise ValueError("Binary masks must contain only 0 and 1, not probabilities")
    return mask.astype(bool, copy=False)


def binary_metrics(prediction: np.ndarray, target: np.ndarray) -> dict[str, Any]:
    """Score one image, with double-empty Dice/IoU=1 and single-empty=0.

    Undefined precision/recall are 1 only for a double-empty pair, otherwise 0.
    All aggregation is image-macro, so large images do not dominate small ones.
    """
    predicted = _binary_mask(prediction)
    truth = _binary_mask(target)
    if predicted.shape != truth.shape:
        raise ValueError(f"Mask shapes differ: {predicted.shape} versus {truth.shape}")
    tp = int(np.count_nonzero(predicted & truth))
    fp = int(np.count_nonzero(predicted & ~truth))
    fn = int(np.count_nonzero(~predicted & truth))
    tn = int(np.count_nonzero(~predicted & ~truth))
    predicted_pixels, target_pixels = tp + fp, tp + fn
    double_empty = predicted_pixels == target_pixels == 0
    return {
        "dice": 2.0 * tp / (predicted_pixels + target_pixels) if not double_empty else 1.0,
        "iou": tp / (tp + fp + fn) if not double_empty else 1.0,
        "precision": tp / predicted_pixels if predicted_pixels else float(double_empty),
        "recall": tp / target_pixels if target_pixels else float(double_empty),
        "tp": tp,
        "fp": fp,
        "fn": fn,
        "tn": tn,
        "target_pixels": target_pixels,
        "predicted_pixels": predicted_pixels,
        "image_pixels": int(truth.size),
    }


def aggregate_metrics(rows: Iterable[Mapping[str, Any]]) -> dict[str, Any]:
    """Report all-image and positive-GT means plus empty-GT false positives.

    The empty-GT false-positive rate is the fraction of *images* with any
    predicted foreground. Its mean foreground pixel fraction is also reported.
    Missing cohorts use null, never a misleading zero or perfect score.
    """
    samples = list(rows)

    def macro(cohort: list[Mapping[str, Any]]) -> dict[str, Any]:
        return {
            "image_count": len(cohort),
            **{
                f"macro_{name}": float(np.mean([row[name] for row in cohort])) if cohort else None
                for name in ("dice", "iou", "precision", "recall")
            },
        }

    positives = [row for row in samples if row["target_pixels"] > 0]
    empty = [row for row in samples if row["target_pixels"] == 0]
    false_positives = sum(row["predicted_pixels"] > 0 for row in empty)
    return {
        **macro(samples),
        "positive_cohort": macro(positives),
        "empty_gt_count": len(empty),
        "empty_gt_false_positive_count": false_positives,
        "empty_gt_false_positive_rate": false_positives / len(empty) if empty else None,
        "empty_gt_mean_foreground_fraction": (
            float(np.mean([row["predicted_pixels"] / row["image_pixels"] for row in empty]))
            if empty
            else None
        ),
    }
