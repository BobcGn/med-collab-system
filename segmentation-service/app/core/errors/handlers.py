from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.errors.exceptions import SegmentationServiceError

logger = logging.getLogger(__name__)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(SegmentationServiceError)
    async def handle_segmentation_error(
        _: Request,
        error: SegmentationServiceError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=error.status_code,
            content={
                "error_code": error.error_code,
                "message": error.message,
                "detail": error.detail,
            },
        )

    @app.exception_handler(Exception)
    async def handle_unexpected_error(_: Request, error: Exception) -> JSONResponse:
        logger.exception("Unhandled segmentation-service error", exc_info=error)
        return JSONResponse(
            status_code=500,
            content={
                "error_code": "INTERNAL_SERVER_ERROR",
                "message": "Unexpected server error",
                "detail": str(error),
            },
        )

