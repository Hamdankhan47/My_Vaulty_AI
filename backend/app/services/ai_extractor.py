from abc import ABC, abstractmethod
from app.schemas.extraction import ExtractionResponse


class AIExtractor(ABC):

  @abstractmethod
  async def extract(
      self, document_type: str | None, ocr_text: str
  ) -> ExtractionResponse:
    """Extract structured document fields from raw OCR text using AI."""
    pass
