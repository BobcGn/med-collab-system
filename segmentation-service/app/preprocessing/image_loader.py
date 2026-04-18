from __future__ import annotations

import base64
import io
import urllib.request
from pathlib import Path
from urllib.parse import unquote_to_bytes

import numpy as np
from PIL import Image, UnidentifiedImageError

from app.core.errors.exceptions import InvalidImageError, UnsupportedImageSourceError
from app.schemas.internal.segmentation import LoadedRasterImage, PreparedSegmentationInput


class ImageLoader:
    """Decode input references into grayscale and RGB rasters.

    The loader keeps the original raster geometry because the post-processing step
    needs to project segmentation results back to original image coordinates.
    """

    def load(self, request: PreparedSegmentationInput) -> LoadedRasterImage:
        if request.source_type == "LOCAL_FILE":
            image_bytes = self._load_local_file(request.image_reference)
        elif request.source_type == "DATA_URL":
            image_bytes = self._load_data_url(request.image_reference)
        elif request.source_type == "OBJECT_STORAGE":
            image_bytes = self._load_remote_object(request.image_reference)
        elif request.source_type == "INLINE_REFERENCE":
            raise UnsupportedImageSourceError(
                message="Inline reference does not include raster bytes",
                detail=request.image_reference,
            )
        else:
            raise UnsupportedImageSourceError(
                message="Unsupported image source type",
                detail=request.source_type,
            )

        return self._decode_image(
            image_bytes=image_bytes,
            source_type=request.source_type,
            reference=request.image_reference,
            mime_type=request.mime_type,
        )

    def _load_local_file(self, image_reference: str) -> bytes:
        path = Path(image_reference).expanduser()
        if not path.exists() or not path.is_file():
            raise InvalidImageError(
                message="Local image file does not exist",
                detail=str(path),
            )
        return path.read_bytes()

    def _load_data_url(self, image_reference: str) -> bytes:
        header, _, payload = image_reference.partition(",")
        if not payload:
            raise InvalidImageError(message="Data URL payload is empty")

        if ";base64" in header.lower():
            try:
                return base64.b64decode(payload, validate=True)
            except ValueError as error:
                raise InvalidImageError(message="Data URL base64 payload is invalid") from error

        return unquote_to_bytes(payload)

    def _load_remote_object(self, image_reference: str) -> bytes:
        if not image_reference.startswith(("http://", "https://")):
            raise UnsupportedImageSourceError(
                message="OBJECT_STORAGE currently expects an HTTP(S) URL",
                detail=image_reference,
            )

        with urllib.request.urlopen(image_reference, timeout=10) as response:
            return response.read()

    def _decode_image(
        self,
        image_bytes: bytes,
        source_type: str,
        reference: str,
        mime_type: str | None,
    ) -> LoadedRasterImage:
        try:
            with Image.open(io.BytesIO(image_bytes)) as image:
                rgb_image = image.convert("RGB")
                grayscale_image = image.convert("L")
                rgb_array = np.asarray(rgb_image, dtype=np.uint8)
                grayscale_array = np.asarray(grayscale_image, dtype=np.float32) / 255.0
        except UnidentifiedImageError as error:
            raise InvalidImageError(message="Input image bytes cannot be decoded by Pillow") from error

        return LoadedRasterImage(
            grayscale=grayscale_array,
            preview_rgb=rgb_array,
            width=rgb_array.shape[1],
            height=rgb_array.shape[0],
            source_type=source_type,
            reference=reference,
            mime_type=mime_type,
        )

