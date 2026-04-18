class SegmentationServiceError(Exception):
    """Base exception for predictable service errors exposed through the API."""

    error_code = "SEGMENTATION_SERVICE_ERROR"
    status_code = 400

    def __init__(self, message: str, detail: str | None = None):
        super().__init__(message)
        self.message = message
        self.detail = detail


class ConfigurationError(SegmentationServiceError):
    """Raised when the service configuration is internally inconsistent."""

    error_code = "CONFIGURATION_ERROR"
    status_code = 500


class UnsupportedImageSourceError(SegmentationServiceError):
    error_code = "UNSUPPORTED_IMAGE_SOURCE"
    status_code = 400


class InferenceUnavailableError(SegmentationServiceError):
    error_code = "INFERENCE_UNAVAILABLE"
    status_code = 503


class DependencyMissingError(SegmentationServiceError):
    """Raised when an optional runtime dependency such as Torch is missing."""

    error_code = "DEPENDENCY_MISSING"
    status_code = 500


class InvalidImageError(SegmentationServiceError):
    """Raised when an input image cannot be decoded into a raster image."""

    error_code = "INVALID_IMAGE"
    status_code = 400


class ArtifactStorageError(SegmentationServiceError):
    """Raised when mask or metadata artifacts cannot be written to disk."""

    error_code = "ARTIFACT_STORAGE_ERROR"
    status_code = 500
