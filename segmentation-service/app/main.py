from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.router import api_router, health_router
from app.core.config.settings import get_settings
from app.core.errors.handlers import register_exception_handlers
from app.core.logging.setup import configure_logging

logger = logging.getLogger(__name__)


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        logger.info(
            "Starting %s v%s with backend=%s model=%s:%s",
            settings.app_name,
            settings.app_version,
            settings.inference_backend,
            settings.model_name,
            settings.model_version,
        )
        yield
        logger.info("Stopping %s", settings.app_name)

    application = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        lifespan=lifespan,
    )
    application.include_router(health_router)
    application.include_router(api_router, prefix=settings.api_prefix)
    register_exception_handlers(application)
    return application


app = create_app()

