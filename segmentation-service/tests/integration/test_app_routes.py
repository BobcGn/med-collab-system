from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path

try:
    import numpy as np
    from fastapi.testclient import TestClient
    from PIL import Image
except ModuleNotFoundError:  # pragma: no cover - local environment may not have dependencies yet.
    np = None
    TestClient = None
    Image = None

from app.core.config.settings import get_settings


@unittest.skipIf(np is None or TestClient is None or Image is None, "runtime dependencies are not installed")
class AppRoutesIntegrationTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self._temp_dir = tempfile.TemporaryDirectory()
        self.artifacts_dir = Path(self._temp_dir.name) / "artifacts"

        os.environ["SEGMENTATION_SERVICE_INFERENCE_BACKEND"] = "mock"
        os.environ["SEGMENTATION_SERVICE_ARTIFACTS_DIR"] = str(self.artifacts_dir)
        get_settings.cache_clear()

        from app.main import create_app

        self.client = TestClient(create_app())

    def tearDown(self) -> None:
        os.environ.pop("SEGMENTATION_SERVICE_INFERENCE_BACKEND", None)
        os.environ.pop("SEGMENTATION_SERVICE_ARTIFACTS_DIR", None)
        get_settings.cache_clear()
        self._temp_dir.cleanup()

    def test_health_endpoint_returns_ok(self) -> None:
        response = self.client.get("/health")

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["status"], "ok")
        self.assertEqual(payload["backend"], "mock")

    def test_segment_endpoint_returns_structured_regions(self) -> None:
        image_path = Path(self._temp_dir.name) / "sample.png"
        canvas = np.zeros((96, 96), dtype=np.uint8)
        canvas[24:72, 28:76] = 200
        Image.fromarray(canvas, mode="L").save(image_path)

        response = self.client.post(
            "/api/v1/segment",
            json={
                "hospital_id": "H-001",
                "patient_id": "P-001",
                "patient_name": "Alice",
                "image_type": "CT",
                "image": {
                    "source_type": "LOCAL_FILE",
                    "value": str(image_path),
                },
                "options": {
                    "return_mask": True,
                    "return_contour": True,
                    "return_metrics": True,
                },
            },
        )

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["status"], "completed")
        self.assertEqual(payload["quality_gate"]["status"], "PASS")
        self.assertTrue(payload["regions"])
        self.assertTrue(payload["artifacts"]["mask_path"])

