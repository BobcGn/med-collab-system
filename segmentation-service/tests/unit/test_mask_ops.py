from __future__ import annotations

import unittest

try:
    import numpy as np
except ModuleNotFoundError:  # pragma: no cover - local environment may not have dependencies yet.
    np = None

from app.postprocessing.mask_ops import extract_region_computations


@unittest.skipIf(np is None, "numpy is not installed")
class MaskOpsTestCase(unittest.TestCase):
    def test_extract_region_computations_builds_single_component(self) -> None:
        binary_mask = np.zeros((10, 10), dtype=np.uint8)
        binary_mask[2:6, 3:7] = 1

        probability_map = np.zeros((10, 10), dtype=np.float32)
        probability_map[2:6, 3:7] = 0.92

        computations = extract_region_computations(
            request_id="seg-1",
            probability_map=probability_map,
            binary_mask=binary_mask,
            min_region_pixels=4,
            pixel_spacing_mm=0.7,
            max_contour_points=16,
        )

        self.assertEqual(len(computations), 1)
        self.assertEqual(computations[0].region_id, "seg-1-region-1")
        self.assertGreater(computations[0].mean_confidence, 0.9)
        self.assertGreater(computations[0].width_percent, 30.0)
        self.assertTrue(computations[0].contour)

