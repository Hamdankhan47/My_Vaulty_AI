from pydantic import BaseModel, Field


class ExtractionRequest(BaseModel):
  document_type: str | None = Field(
      default=None,
      description="Optional document type context if known (e.g. ELECTRICITY_BILL)",
  )
  ocr_text: str = Field(
      ...,
      min_length=1,
      description="Raw OCR text extracted from the document",
  )


class ExtractedField(BaseModel):
  name: str = Field(..., description="Field name, e.g. Provider, Due Date")
  value: str = Field(..., description="Field value, e.g. IESCO, 2026-09-18")
  type: str = Field(
      ...,
      description="Field type: TEXT, NUMBER, or DATE (must be uppercase)",
  )


class ExtractionResponse(BaseModel):
  document_type: str = Field(
      ...,
      description=(
          "Inferred or verified document type (e.g. ELECTRICITY_BILL, UNKNOWN)"
      ),
  )
  fields: list[ExtractedField] = Field(
      default_factory=list, description="Extracted structured fields"
  )
