from __future__ import annotations

from collections import deque
from math import atan2

import numpy as np

from app.schemas.internal.segmentation import RegionComputation


def extract_region_computations(
    request_id: str,
    probability_map: np.ndarray,
    binary_mask: np.ndarray,
    *,
    min_region_pixels: int,
    pixel_spacing_mm: float,
    max_contour_points: int,
) -> list[RegionComputation]:
    """Convert a binary mask into region-level measurements.

    The implementation intentionally avoids OpenCV or SciPy so that the service
    keeps a small dependency surface. Connectivity is computed with a simple BFS.
    """

    height, width = binary_mask.shape
    visited = np.zeros_like(binary_mask, dtype=bool)
    computations: list[RegionComputation] = []

    for y in range(height):
        for x in range(width):
            if binary_mask[y, x] == 0 or visited[y, x]:
                continue

            component_pixels = _collect_component(binary_mask, visited, start_y=y, start_x=x)
            if len(component_pixels) < min_region_pixels:
                continue

            coords = np.asarray(component_pixels, dtype=np.int32)
            ys = coords[:, 0]
            xs = coords[:, 1]
            top = int(ys.min())
            bottom = int(ys.max())
            left = int(xs.min())
            right = int(xs.max())
            pixel_count = int(coords.shape[0])

            component_mask = np.zeros_like(binary_mask, dtype=np.uint8)
            component_mask[ys, xs] = 1
            contour = _extract_contour(component_mask, coords, max_contour_points)

            width_px = max(1, (right - left) + 1)
            height_px = max(1, (bottom - top) + 1)
            mean_confidence = float(probability_map[ys, xs].mean())
            coverage_percent = float(pixel_count / float(height * width) * 100.0)
            estimated_size_mm = float(max(width_px, height_px) * pixel_spacing_mm)
            center_x = float(xs.mean())
            center_y = float(ys.mean())

            computations.append(
                RegionComputation(
                    region_id=f"{request_id}-region-{len(computations) + 1}",
                    pixel_count=pixel_count,
                    mean_confidence=mean_confidence,
                    coverage_percent=coverage_percent,
                    estimated_size_mm=estimated_size_mm,
                    left_percent=_to_percent(left, width),
                    top_percent=_to_percent(top, height),
                    width_percent=_to_percent(width_px, width),
                    height_percent=_to_percent(height_px, height),
                    center_x_percent=_to_percent(center_x, width),
                    center_y_percent=_to_percent(center_y, height),
                    contour=contour,
                )
            )

    computations.sort(key=lambda item: item.mean_confidence, reverse=True)
    return computations


def _collect_component(
    mask: np.ndarray,
    visited: np.ndarray,
    *,
    start_y: int,
    start_x: int,
) -> list[tuple[int, int]]:
    queue: deque[tuple[int, int]] = deque([(start_y, start_x)])
    visited[start_y, start_x] = True
    component_pixels: list[tuple[int, int]] = []
    height, width = mask.shape

    while queue:
        y, x = queue.popleft()
        component_pixels.append((y, x))

        for offset_y, offset_x in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            next_y = y + offset_y
            next_x = x + offset_x
            if next_y < 0 or next_y >= height or next_x < 0 or next_x >= width:
                continue
            if visited[next_y, next_x] or mask[next_y, next_x] == 0:
                continue
            visited[next_y, next_x] = True
            queue.append((next_y, next_x))

    return component_pixels


def _extract_contour(
    component_mask: np.ndarray,
    coords: np.ndarray,
    max_contour_points: int,
) -> list[tuple[float, float]]:
    height, width = component_mask.shape
    boundary: list[tuple[int, int]] = []

    for y, x in coords:
        if (
            y == 0
            or x == 0
            or y == height - 1
            or x == width - 1
            or component_mask[y - 1, x] == 0
            or component_mask[y + 1, x] == 0
            or component_mask[y, x - 1] == 0
            or component_mask[y, x + 1] == 0
        ):
            boundary.append((int(y), int(x)))

    if not boundary:
        return []

    center_y = float(coords[:, 0].mean())
    center_x = float(coords[:, 1].mean())
    boundary.sort(key=lambda point: atan2(point[0] - center_y, point[1] - center_x))

    if len(boundary) > max_contour_points:
        step = max(1, len(boundary) // max_contour_points)
        boundary = boundary[::step][:max_contour_points]

    return [(_to_percent(x, width), _to_percent(y, height)) for y, x in boundary]


def _to_percent(value: float, total: int) -> float:
    if total <= 0:
        return 0.0
    return round(float(value) / float(total) * 100.0, 4)
