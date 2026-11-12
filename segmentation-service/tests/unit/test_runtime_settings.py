from __future__ import annotations

from pathlib import Path

import pytest

from app.core.config.settings import (
    BASE_DIR,
    ENV_FIELD_MAP,
    Settings,
    effective_model_settings,
    get_settings,
)
from app.core.errors.exceptions import InferenceUnavailableError


@pytest.fixture(autouse=True)
def isolated_settings(monkeypatch):
    for variable in (*ENV_FIELD_MAP, "SEGMENTATION_SERVICE_CONFIG_FILE"):
        monkeypatch.delenv(variable, raising=False)
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


def test_model_manifest_toml_and_environment_resolution(tmp_path, monkeypatch) -> None:
    config = tmp_path / "app.toml"
    config.write_text('[model]\nmanifest_path = "bundles/toml/manifest.json"\n')
    monkeypatch.setenv("SEGMENTATION_SERVICE_CONFIG_FILE", str(config))
    settings = get_settings()
    assert settings.manifest_path == BASE_DIR / "bundles/toml/manifest.json"
    assert settings.input_height == 256  # Partial TOML retains model defaults.
    monkeypatch.setenv("SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH", "bundles/env/manifest.json")
    get_settings.cache_clear()
    settings = get_settings()
    assert settings.manifest_path == BASE_DIR / "bundles/env/manifest.json"
    assert Settings(model_manifest_path=str(tmp_path / "manifest.json")).manifest_path == (
        tmp_path / "manifest.json"
    )
    assert Settings(model_manifest_path="~/manifest.json").manifest_path == (
        Path.home() / "manifest.json"
    )


def test_legacy_settings_are_unchanged_and_spacing_can_be_unknown() -> None:
    settings = Settings()
    assert effective_model_settings(settings) is settings
    assert settings.min_region_pixels == 24
    assert settings.pixel_spacing_mm == 0.7
    assert settings.bundle_manifest_sha256 is None
    assert Settings(pixel_spacing_mm=None).pixel_spacing_mm is None
    loaded = get_settings()
    assert loaded.min_region_pixels == 24
    assert "min_region_pixels" not in loaded.model_fields_set


def test_explicit_cleanup_settings_remain_distinguishable(tmp_path, monkeypatch) -> None:
    assert "min_region_pixels" in Settings(min_region_pixels=24).model_fields_set
    config = tmp_path / "app.toml"
    config.write_text("[inference]\nmin_region_pixels = 24\n")
    monkeypatch.setenv("SEGMENTATION_SERVICE_CONFIG_FILE", str(config))
    settings = get_settings()
    assert settings.min_region_pixels == 24
    assert "min_region_pixels" in settings.model_fields_set
    monkeypatch.delenv("SEGMENTATION_SERVICE_CONFIG_FILE")
    monkeypatch.setenv("SEGMENTATION_SERVICE_MIN_REGION_PIXELS", "24")
    get_settings.cache_clear()
    assert "min_region_pixels" in get_settings().model_fields_set


def test_missing_manifest_fails_as_inference_unavailable(tmp_path) -> None:
    with pytest.raises(InferenceUnavailableError, match="unavailable or incompatible"):
        effective_model_settings(Settings(model_manifest_path=str(tmp_path / "missing.json")))
