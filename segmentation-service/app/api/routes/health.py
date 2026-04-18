from fastapi import APIRouter, Depends

from app.api.dependencies.services import get_runtime_settings
from app.core.config.settings import Settings
from app.schemas.responses.health import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health_check(settings: Settings = Depends(get_runtime_settings)) -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=settings.app_name,
        version=settings.app_version,
        environment=settings.environment,
        backend=settings.inference_backend,
        model_name=settings.model_name,
        model_version=settings.model_version,
    )

