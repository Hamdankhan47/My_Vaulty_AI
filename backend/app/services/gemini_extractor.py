import json
import logging
from google import genai
from google.genai import types
from app.core.config import settings
from app.schemas.extraction import ExtractedField, ExtractionResponse
from app.services.ai_extractor import AIExtractor

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are an expert AI document parsing assistant for MyVault.
Your task is to analyze raw OCR text and extract structured document fields.

RULES:
1. Extract information ONLY from the supplied OCR text. Do not invent information or use outside knowledge.
2. If a field cannot be reliably extracted, omit it. Do not create empty fields or guess values.
3. Normalize values:
   - Convert monetary amounts to numeric representation without currency symbols (e.g. "Rs. 8,450" -> "8450").
   - Convert dates to ISO 8601 format: YYYY-MM-DD (e.g. "18-09-2026" -> "2026-09-18").
4. Field 'type' MUST strictly be one of: "TEXT", "NUMBER", "DATE" (uppercase).
5. 'document_type' MUST be one of:
   "ELECTRICITY_BILL", "GAS_BILL", "INTERNET_BILL", "BANK_STATEMENT", "WARRANTY_CARD", "CERTIFICATE", "RECEIPT", or "UNKNOWN".
6. If the input specifies a known document_type, preserve or verify it as context.
7. Return ONLY valid JSON matching this exact structure:
{
  "document_type": "ELECTRICITY_BILL",
  "fields": [
    {"name": "Provider", "value": "IESCO", "type": "TEXT"},
    {"name": "Amount", "value": "8450", "type": "NUMBER"},
    {"name": "Due Date", "value": "2026-09-18", "type": "DATE"}
  ]
}
"""


class GeminiAIExtractor(AIExtractor):

  def __init__(
      self, api_key: str | None = None, model_name: str | None = None
  ):
    self.api_key = api_key or settings.gemini_api_key
    self.model_name = model_name or settings.gemini_model

  async def extract(
      self, document_type: str | None, ocr_text: str
  ) -> ExtractionResponse:
    if not self.api_key:
      raise ValueError("Gemini API key is not configured")

    user_context = f"Document Type Context: {document_type or 'UNKNOWN'}\n\nRaw OCR Text:\n{ocr_text}"

    client = genai.Client(api_key=self.api_key)
    response = client.models.generate_content(
        model=self.model_name,
        contents=f"{SYSTEM_PROMPT}\n\n{user_context}",
        config=types.GenerateContentConfig(
            temperature=0.1,
            response_mime_type="application/json",
        ),
    )

    return self._parse_and_validate(response.text)

  def _parse_and_validate(self, raw_json: str) -> ExtractionResponse:
    clean_json = raw_json.strip()
    if clean_json.startswith("```json"):
      clean_json = clean_json[7:]
    if clean_json.startswith("```"):
      clean_json = clean_json[3:]
    if clean_json.endswith("```"):
      clean_json = clean_json[:-3]
    clean_json = clean_json.strip()

    data = json.loads(clean_json)

    raw_fields = data.get("fields", [])
    valid_fields = []
    for f in raw_fields:
      name = str(f.get("name", "")).strip()
      value = str(f.get("value", "")).strip()
      ftype = str(f.get("type", "TEXT")).upper().strip()

      if ftype not in ("TEXT", "NUMBER", "DATE"):
        ftype = "TEXT"

      if name and value:
        valid_fields.append(ExtractedField(name=name, value=value, type=ftype))

    doc_type = str(data.get("document_type", "UNKNOWN")).upper().strip()
    return ExtractionResponse(document_type=doc_type, fields=valid_fields)
