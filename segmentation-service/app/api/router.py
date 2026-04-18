from fastapi import APIRouter

from app.api.routes.health import router as health
from app.api.routes.models import router as models
from app.api.routes.segmentation import router as segmentation

health_router = APIRouter()
health_router.include_router(health)

api_router = APIRouter()
api_router.include_router(models)
api_router.include_router(segmentation)

