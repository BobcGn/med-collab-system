"""Synthetic masks only: these checks are not evidence of real-data accuracy."""

from __future__ import annotations

import numpy as np
import pytest

from app.training.metrics import aggregate_metrics, binary_metrics, remove_small_components


def test_empty_mask_conventions_and_undefined_precision_recall() -> None:
    empty = np.zeros((3, 4), dtype=np.uint8)
    positive = empty.copy()
    positive[1, 2] = 1
    double_empty = binary_metrics(empty, empty)
    assert [double_empty[key] for key in ("dice", "iou", "precision", "recall")] == [1] * 4
    for prediction, target in ((positive, empty), (empty, positive)):
        single_empty = binary_metrics(prediction, target)
        assert [single_empty[key] for key in ("dice", "iou", "precision", "recall")] == [0] * 4


def test_confusion_counts_and_segmentation_metrics() -> None:
    prediction = np.array([[1, 1, 0], [0, 0, 1]], dtype=np.uint8)
    target = np.array([[1, 0, 1], [0, 0, 1]], dtype=np.uint8)
    result = binary_metrics(prediction, target)
    assert {key: result[key] for key in ("tp", "fp", "fn", "tn")} == {
        "tp": 2,
        "fp": 1,
        "fn": 1,
        "tn": 2,
    }
    assert result["dice"] == pytest.approx(2 / 3)
    assert result["iou"] == pytest.approx(1 / 2)
    assert result["precision"] == result["recall"] == pytest.approx(2 / 3)
    assert "accuracy" not in result


def test_macro_positive_cohort_and_empty_gt_false_positive_rate() -> None:
    empty = np.zeros((2, 2), dtype=np.uint8)
    positive = np.ones((2, 2), dtype=np.uint8)
    rows = [
        binary_metrics(positive, positive),
        binary_metrics(empty, empty),
        binary_metrics(positive, empty),
    ]
    result = aggregate_metrics(rows)
    assert result["image_count"] == 3
    assert result["macro_dice"] == result["macro_iou"] == pytest.approx(2 / 3)
    assert result["positive_cohort"]["image_count"] == 1
    assert result["positive_cohort"]["macro_dice"] == 1
    assert result["empty_gt_count"] == 2
    assert result["empty_gt_false_positive_count"] == 1
    assert result["empty_gt_false_positive_rate"] == pytest.approx(0.5)
    assert result["empty_gt_mean_foreground_fraction"] == pytest.approx(0.5)
    assert aggregate_metrics([])["macro_dice"] is None
    assert aggregate_metrics(rows[:1])["empty_gt_false_positive_rate"] is None
    assert aggregate_metrics(rows[1:])["positive_cohort"]["macro_dice"] is None


def test_macro_is_not_pixel_weighted() -> None:
    large = np.ones((20, 20), dtype=np.uint8)
    small = np.ones((1, 1), dtype=np.uint8)
    result = aggregate_metrics([binary_metrics(large, large), binary_metrics(0 * small, small)])
    assert result["macro_dice"] == result["macro_iou"] == 0.5


def test_masks_cannot_silently_broadcast_or_threshold_probabilities() -> None:
    with pytest.raises(ValueError, match="shapes differ"):
        binary_metrics(np.zeros((2, 1)), np.zeros((2, 2)))
    with pytest.raises(ValueError, match="only 0 and 1"):
        binary_metrics(np.full((2, 2), 0.5), np.zeros((2, 2)))
    with pytest.raises(ValueError, match="nonempty HW"):
        binary_metrics(np.zeros((0, 2)), np.zeros((0, 2)))


def test_bfs_is_four_connected_keeps_exact_minimum_and_preserves_input() -> None:
    mask = np.array([[1, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=np.uint8)
    original = mask.copy()
    expected = np.array([[1, 1, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]], dtype=np.uint8)
    np.testing.assert_array_equal(remove_small_components(mask, 2), expected)
    np.testing.assert_array_equal(mask, original)
    np.testing.assert_array_equal(remove_small_components(mask, 1), mask)
    assert remove_small_components(mask, 2).dtype == np.uint8
    with pytest.raises(ValueError, match="at least 1"):
        remove_small_components(mask, 0)


def test_bce_soft_dice_has_finite_gradients_on_empty_and_positive_masks() -> None:
    torch = pytest.importorskip("torch")
    from app.training.losses import BCEDiceLoss, soft_dice_loss

    logits = torch.zeros((2, 1, 4, 4), dtype=torch.float32, requires_grad=True)
    target = torch.zeros_like(logits)
    target[1, :, 1:3, 1:3] = 1
    dice_loss = soft_dice_loss(logits, target)
    expected_dice = ((1 - 1 / 9) + (1 - 5 / 13)) / 2
    assert dice_loss.item() == pytest.approx(expected_dice)
    loss = BCEDiceLoss()(logits, target)
    assert loss.item() == pytest.approx(np.log(2) + expected_dice)
    loss.backward()
    assert logits.grad is not None
    assert torch.isfinite(logits.grad).all()
    assert logits.grad.abs().sum().item() > 0
