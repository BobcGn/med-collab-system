from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from app.core.config.settings import Settings
from app.core.errors.exceptions import InferenceUnavailableError
from app.inference.engines.torch_unet_engine import TorchUNetEngine


@pytest.mark.parametrize("wrapper", [None, "state_dict", "model_state_dict"])
def test_legacy_checkpoints_load_safely_and_keep_unspecified_intended_use(
    tmp_path, monkeypatch, wrapper
) -> None:
    torch = pytest.importorskip("torch")
    from app.inference.engines.unet_model import UNet2D

    model = UNet2D(1, 1, 8)
    state = model.state_dict()
    path = tmp_path / "weights.pt"
    torch.save({wrapper: state} if wrapper else state, path)
    real_load = torch.load
    calls = []

    def checked_load(*args, **kwargs):
        calls.append(kwargs)
        return real_load(*args, **kwargs)

    monkeypatch.setattr(torch, "load", checked_load)
    engine = TorchUNetEngine(Settings(
        model_weights_path=str(path),
        base_channels=8,
        input_height=32,
        input_width=32,
        allow_random_weights=False,
    ))
    assert engine.weights_loaded is True
    assert len(calls) == 1 and calls[0]["weights_only"] is True
    descriptor = engine.describe_model()
    assert descriptor.intended_use == "unspecified"
    assert descriptor.bundle_manifest_sha256 is None
    for key, value in model.state_dict().items():
        torch.testing.assert_close(engine.model.state_dict()[key], value)


def test_incompatible_raw_weights_fail_without_random_fallback(tmp_path) -> None:
    torch = pytest.importorskip("torch")
    path = tmp_path / "weights.pt"
    torch.save({"unexpected": torch.zeros(1)}, path)
    with pytest.raises(InferenceUnavailableError, match="could not be loaded"):
        TorchUNetEngine(Settings(model_weights_path=str(path), base_channels=8))


@pytest.mark.parametrize("device_type", ["mps", "cuda", "cpu"])
def test_accelerator_duration_measurements_synchronize(device_type) -> None:
    engine = TorchUNetEngine.__new__(TorchUNetEngine)
    engine.device = SimpleNamespace(type=device_type)
    mps_sync, cuda_sync = Mock(), Mock()
    engine.torch = SimpleNamespace(
        mps=SimpleNamespace(synchronize=mps_sync),
        cuda=SimpleNamespace(synchronize=cuda_sync),
    )
    engine._synchronize_device()
    assert mps_sync.call_count == int(device_type == "mps")
    assert cuda_sync.call_count == int(device_type == "cuda")
