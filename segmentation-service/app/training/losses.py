"""FP32 binary U-Net objectives; logits, not thresholded masks, enter the loss."""

from __future__ import annotations

import torch
from torch import Tensor, nn


def soft_dice_loss(logits: Tensor, target: Tensor, smooth: float = 1.0) -> Tensor:
    """Mean per-image soft Dice loss, including finite empty-GT examples."""
    if logits.shape != target.shape or logits.ndim != 4 or logits.shape[1] != 1:
        raise ValueError("Binary logits and targets must have the same N1HW shape")
    if smooth <= 0:
        raise ValueError("Dice smoothing must be positive")
    probabilities = torch.sigmoid(logits)
    dimensions = (1, 2, 3)
    intersection = (probabilities * target).sum(dim=dimensions)
    denominator = probabilities.sum(dim=dimensions) + target.sum(dim=dimensions)
    return (1.0 - (2.0 * intersection + smooth) / (denominator + smooth)).mean()


class BCEDiceLoss(nn.Module):
    """Unweighted BCEWithLogitsLoss + soft Dice loss (smoothing=1)."""

    def __init__(self) -> None:
        super().__init__()
        self.bce = nn.BCEWithLogitsLoss()

    def forward(self, logits: Tensor, target: Tensor) -> Tensor:
        return self.bce(logits, target) + soft_dice_loss(logits, target)
