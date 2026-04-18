from __future__ import annotations

import unittest

try:
    import torch
except ModuleNotFoundError:  # pragma: no cover - local environment may not have dependencies yet.
    torch = None

if torch is not None:
    from app.inference.engines.unet_model import UNet2D


@unittest.skipIf(torch is None, "torch is not installed")
class UNetModelTestCase(unittest.TestCase):
    def test_unet_preserves_spatial_shape(self) -> None:
        model = UNet2D(in_channels=1, out_channels=1, base_channels=16)
        batch = torch.randn(2, 1, 128, 128)

        logits = model(batch)

        self.assertEqual(tuple(logits.shape), (2, 1, 128, 128))

