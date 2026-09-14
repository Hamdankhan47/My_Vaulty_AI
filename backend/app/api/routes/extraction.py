from app.core.config import settings
from app.schemas.extraction import ExtractionRequest, ExtractionResponse
from app.services.gemini_extractor import GeminiAIExtractor
from fastapi import APIRouter, HTTPException, status

router = APIRouter(prefix="/api/v1/documents", tags=["extraction"])


@router.post(
    "/extract",
    response_model=ExtractionResponse,
    status_code=status.HTTP_200_OK,
    summary="Extract structured document fields from OCR text using Gemini AI",
)
async def extract_document_fields(
    request: ExtractionRequest,
) -> ExtractionResponse:
  if not request.ocr_text.strip():
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail="OCR text cannot be empty",
    )

  if not settings.gemini_api_key.strip():
    raise HTTPException(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        detail=(
            "Server configuration error: Gemini API key is missing or not"
            " configured"
        ),
    )

  try:
    extractor = GeminiAIExtractor()
    return await extractor.extract(
        document_type=request.document_type, ocr_text=request.ocr_text
    )
  except ValueError as val_err:
    raise HTTPException(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(val_err)
    )
  except Exception as err:
    raise HTTPException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        detail=f"AI extraction failed: {str(err)}",
    )
