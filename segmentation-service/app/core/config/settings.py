from __future__ import annotations

import os
import tomllib
from functools import lru_cache
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

BASE_DIR = Path(__file__).resolve().parents[3]
DEFAULT_CONFIGS = {
    "local": BASE_DIR / "configs" / "local" / "app.toml",
    "test": BASE_DIR / "configs" / "test" / "app.toml",
}
ENV_FIELD_MAP = {
    "SEGMENTATION_SERVICE_ENVIRONMENT": "environment",
    "SEGMENTATION_SERVICE_APP_NAME": "app_name",
    "SEGMENTATION_SERVICE_APP_VERSION": "app_version",
    "SEGMENTATION_SERVICE_API_PREFIX": "api_prefix",
    "SEGMENTATION_SERVICE_HOST": "host",
    "SEGMENTATION_SERVICE_PORT": "port",
    "SEGMENTATION_SERVICE_LOG_LEVEL": "log_level",
    "SEGMENTATION_SERVICE_INFERENCE_BACKEND": "inference_backend",
    "SEGMENTATION_SERVICE_MODEL_NAME": "model_name",
    "SEGMENTATION_SERVICE_MODEL_VERSION": "model_version",
    "SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH": "model_weights_path",
    "SEGMENTATION_SERVICE_DEVICE": "device",
    "SEGMENTATION_SERVICE_ALLOW_RANDOM_WEIGHTS": "allow_random_weights",
    "SEGMENTATION_SERVICE_INPUT_HEIGHT": "input_height",
    "SEGMENTATION_SERVICE_INPUT_WIDTH": "input_width",
    "SEGMENTATION_SERVICE_IN_CHANNELS": "in_channels",
    "SEGMENTATION_SERVICE_OUTPUT_CLASSES": "output_classes",
    "SEGMENTATION_SERVICE_BASE_CHANNELS": "base_channels",
    "SEGMENTATION_SERVICE_LESION_CLASS_INDEX": "lesion_class_index",
    "SEGMENTATION_SERVICE_PROBABILITY_THRESHOLD": "probability_threshold",
    "SEGMENTATION_SERVICE_MIN_REGION_PIXELS": "min_region_pixels",
    "SEGMENTATION_SERVICE_MIN_CONFIDENCE": "min_confidence",
    "SEGMENTATION_SERVICE_PIXEL_SPACING_MM": "pixel_spacing_mm",
    "SEGMENTATION_SERVICE_MAX_CONTOUR_POINTS": "max_contour_points",
    "SEGMENTATION_SERVICE_REQUEST_TIMEOUT_SECONDS": "request_timeout_seconds",
    "SEGMENTATION_SERVICE_ARTIFACTS_DIR": "artifacts_dir",
    "SEGMENTATION_SERVICE_RETURN_ARTIFACTS": "return_artifacts",
}


class Settings(BaseModel):
    """Application-wide settings used by the API, pipeline, and backends."""

    model_config = ConfigDict(extra="ignore")

    environment: str = "local"
    app_name: str = "segmentation-service"
    app_version: str = "0.1.0"
    api_prefix: str = "/api/v1"
    host: str = "127.0.0.1"
    port: int = 8099
    log_level: str = "INFO"

    inference_backend: str = "torch_unet"
    device: str = "cpu"
    request_timeout_seconds: int = 30
    probability_threshold: float = Field(default=0.5, ge=0.0, le=1.0)
    min_region_pixels: int = Field(default=24, ge=1)
    min_confidence: float = Field(default=0.55, ge=0.0, le=1.0)
    pixel_spacing_mm: float = Field(default=0.7, gt=0.0)
    max_contour_points: int = Field(default=48, ge=4)

    model_name: str = "unet-bootstrap"
    model_version: str = "0.1.0"
    model_weights_path: str | None = None
    allow_random_weights: bool = True
    input_height: int = Field(default=256, ge=32)
    input_width: int = Field(default=256, ge=32)
    in_channels: int = Field(default=1, ge=1)
    output_classes: int = Field(default=1, ge=1)
    base_channels: int = Field(default=32, ge=8)
    lesion_class_index: int = Field(default=0, ge=0)

    artifacts_dir: str = "var/artifacts"
    return_artifacts: bool = True
    loaded_config_file: str | None = None

    @property
    def artifacts_path(self) -> Path:
        """Resolve the artifact output directory relative to the service root."""

        path = Path(self.artifacts_dir)
        if path.is_absolute():
            return path
        return (BASE_DIR / path).resolve()

    @property
    def weights_path(self) -> Path | None:
        """Return the optional model weights path if it is configured."""

        if not self.model_weights_path:
            return None
        path = Path(self.model_weights_path)
        if path.is_absolute():
            return path
        return (BASE_DIR / path).resolve()


def _read_toml(path: Path) -> dict[str, Any]:
    """Flatten the nested TOML structure into the Settings model fields."""

    if not path.exists():
        return {}

    with path.open("rb") as file:
        raw = tomllib.load(file)

    service = raw.get("service", {})
    api = raw.get("api", {})
    logging = raw.get("logging", {})
    inference = raw.get("inference", {})
    model = raw.get("model", {})
    storage = raw.get("storage", {})

    return {
        "environment": service.get("environment"),
        "app_name": service.get("app_name"),
        "app_version": service.get("app_version"),
        "api_prefix": api.get("prefix"),
        "host": api.get("host"),
        "port": api.get("port"),
        "log_level": logging.get("level"),
        "inference_backend": inference.get("backend"),
        "device": inference.get("device"),
        "request_timeout_seconds": inference.get("request_timeout_seconds"),
        "probability_threshold": inference.get("probability_threshold"),
        "min_region_pixels": inference.get("min_region_pixels"),
        "min_confidence": inference.get("min_confidence"),
        "pixel_spacing_mm": inference.get("pixel_spacing_mm"),
        "max_contour_points": inference.get("max_contour_points"),
        "model_name": model.get("name"),
        "model_version": model.get("version"),
        "model_weights_path": model.get("weights_path"),
        "allow_random_weights": model.get("allow_random_weights"),
        "input_height": model.get("input_height"),
        "input_width": model.get("input_width"),
        "in_channels": model.get("in_channels"),
        "output_classes": model.get("output_classes"),
        "base_channels": model.get("base_channels"),
        "lesion_class_index": model.get("lesion_class_index"),
        "artifacts_dir": storage.get("artifacts_dir"),
        "return_artifacts": storage.get("return_artifacts"),
        "loaded_config_file": str(path),
    }


def _env_overrides() -> dict[str, Any]:
    """Collect explicit environment variable overrides for the settings model."""

    return {
        field: value
        for env_name, field in ENV_FIELD_MAP.items()
        if (value := os.getenv(env_name)) is not None and value != ""
    }


def _default_config_path(environment: str) -> Path:
    return DEFAULT_CONFIGS.get(environment, DEFAULT_CONFIGS["local"])


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Resolve settings from TOML files first and environment variables second."""

    environment = os.getenv("SEGMENTATION_SERVICE_ENVIRONMENT", "local")
    config_file = os.getenv("SEGMENTATION_SERVICE_CONFIG_FILE")
    config_path = Path(config_file).expanduser() if config_file else _default_config_path(environment)

    data = _read_toml(config_path)
    data.update(_env_overrides())

    if data.get("loaded_config_file") is None and config_path.exists():
        data["loaded_config_file"] = str(config_path)

    return Settings.model_validate(data)
