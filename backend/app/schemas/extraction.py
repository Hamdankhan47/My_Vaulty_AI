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
      description=(
          "Field type: TEXT, NUMBER, DATE, CURRENCY, PHONE, EMAIL, URL, ADDRESS,"
          " PERSON_NAME, or ORGANIZATION (must be uppercase)"
      ),
  )


class ExtractionResponse(BaseModel):
  category: str | None = Field(
      default="Other",
      description="Inferred document category (e.g. Bill, Banking, Receipt, Other)",
  )
  document_type: str = Field(
      ...,
      description=(
          "Inferred or verified document type (e.g. Electricity Bill, UNKNOWN)"
      ),
  )
  fields: list[ExtractedField] = Field(
      default_factory=list, description="Extracted structured fields"
  )
