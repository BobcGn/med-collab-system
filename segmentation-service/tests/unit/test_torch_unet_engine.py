from __future__ import annotations

import unittest

try:
    import numpy as np
    import torch

    from app.core.config.settings import Settings
    from app.inference.engines.torch_unet_engine import TorchUNetEngine
    from app.schemas.internal.segmentation import LoadedRasterImage, PreparedModelInput, PreparedSegmentationInput
except ModuleNotFoundError:  # pragma: no cover - local environment may not have dependencies yet.
    np = None
    torch = None
    Settings = None
    TorchUNetEngine = None
    LoadedRasterImage = None
    PreparedModelInput = None
    PreparedSegmentationInput = None


@unittest.skipIf(np is None or torch is None or Settings is None, "torch runtime dependencies are not installed")
class TorchUNetEngineTestCase(unittest.TestCase):
    def test_predict_returns_probability_map_from_real_unet(self) -> None:
        settings = Settings(
            inference_backend="torch_unet",
            device="cpu",
            allow_random_weights=True,
            input_height=64,
            input_width=64,
            in_channels=1,
            output_classes=1,
            base_channels=8,
        )
        engine = TorchUNetEngine(settings)

        request = PreparedSegmentationInput(
            request_id="seg-test-1",
            hospital_id="H-001",
            patient_id="P-001",
            patient_name="Alice",
            image_type="CT",
            source_type="LOCAL_FILE",
            image_reference="/tmp/sample.png",
        )
        loaded_image = LoadedRasterImage(
            grayscale=np.zeros((64, 64), dtype=np.float32),
            preview_rgb=np.zeros((64, 64, 3), dtype=np.uint8),
            width=64,
            height=64,
            source_type="LOCAL_FILE",
            reference="/tmp/sample.png",
        )
        prepared_input = PreparedModelInput(
            request=request,
            loaded_image=loaded_image,
            resized_image=np.zeros((64, 64), dtype=np.float32),
            tensor=np.zeros((1, 64, 64), dtype=np.float32),
            model_height=64,
            model_width=64,
            preprocess_ms=0,
        )

        prediction = engine.predict(prepared_input)

        self.assertEqual(prediction.class_index, 0)
        self.assertEqual(prediction.probability_map.shape, (64, 64))
        self.assertGreaterEqual(float(prediction.probability_map.min()), 0.0)
        self.assertLessEqual(float(prediction.probability_map.max()), 1.0)
        self.assertIn("real PyTorch U-Net forward pass", prediction.model_note)
