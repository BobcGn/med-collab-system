from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class BoundingBox:
    left_percent: float
    top_percent: float
    width_percent: float
    height_percent: float


@dataclass(frozen=True, slots=True)
class ContourPoint:
    x_percent: float
    y_percent: float

