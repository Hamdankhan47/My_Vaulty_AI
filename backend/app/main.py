from app.api.routes.extraction import router as extraction_router
from app.core.config import settings
from fastapi import FastAPI

app = FastAPI(
    title=settings.app_name,
    version="1.0.0",
    description="MyVault AI Backend for Document Field Extraction",
)

app.include_router(extraction_router)


@app.get("/health", tags=["health"])
async def health_check():
  return {
      "status": "healthy",
      "app": settings.app_name,
      "environment": settings.environment,
  }
