import asyncio
import json
import logging
import socket
import urllib.error
import urllib.request
from google import genai
from google.genai import types
from app.core.config import settings
from app.schemas.extraction import ExtractedField, ExtractionResponse
from app.services.ai_extractor import AIExtractor

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are an expert AI document parsing assistant for MyVault.
Your task is to analyze raw OCR text and extract comprehensive, accurate, document-aware structured fields.

=== BASE EXTRACTION RULES ===
1. FACTUAL GROUNDING: Extract ONLY information explicitly supported by the OCR text. Never guess, infer missing values, or invent information.
2. OMIT ABSENT FIELDS: If a field does not appear in the OCR text, DO NOT include it. Never output "N/A", "Unknown", "None", or placeholder strings.
3. PRESERVE IDENTIFIERS: Numbers like Consumer Number, Reference Number, Meter Number, Account Number, Invoice Number, CNIC, and Roll Number MUST be assigned type "TEXT" (never "PHONE" or "NUMBER").
4. FIELD TYPE ASSIGNMENT:
   - "TEXT": Identifiers, status strings, plan names, descriptions, or general text.
   - "NUMBER": Counts or meter reading units (e.g., Units Consumed, Previous/Current Meter Reading).
   - "DATE": Dates in ISO 8601 format YYYY-MM-DD.
   - "CURRENCY": Monetary values (clean numeric string without symbols, e.g. "8450" for "Rs. 8,450").
   - "PHONE": Telephone/contact numbers (e.g. "+92 300 1234567").
   - "EMAIL": Email addresses (e.g. "support@example.com").
   - "URL": Website links/URLs (e.g. "https://example.com").
   - "ADDRESS": Consolidated physical addresses.
   - "PERSON_NAME": Customer name, contact person name, policy holder, or card holder.
   - "ORGANIZATION": Provider name, company, merchant, bank, or issuing institution.
5. FIELD NAME STANDARDIZATION: Use clear, canonical field names (e.g. "Consumer Number", "Reference Number", "Meter Number", "Due Date", "Total Amount", "Service Address").
6. NO DUPLICATES: Do not extract the same value multiple times under slightly different labels.
7. ADDRESS CONSOLIDATION: Combine street, sector, city, district, province, and country into ONE consolidated ADDRESS field when referring to the same location.

=== DOMAIN-SPECIFIC EXTRACTION INSTRUCTIONS ===

--- DOMAIN 1: UTILITY BILLS (ELECTRICITY, GAS, WATER, INTERNET, MOBILE, TAX) ---
When processing utility bills (especially Electricity, Gas, or Water Bills), actively inspect the OCR text for all available fields:
- Identification: "Provider" (ORGANIZATION), "Consumer Name" (PERSON_NAME), "Consumer Number" (TEXT), "Reference Number" (TEXT), "Account Number" (TEXT), "Meter Number" (TEXT).
- Billing Details: "Billing Month" (TEXT), "Billing Period" (TEXT), "Issue Date" (DATE), "Due Date" (DATE), "Tariff" (TEXT).
- Meter Readings & Usage: "Previous Reading" (NUMBER), "Current Reading" (NUMBER), "Units Consumed" (NUMBER), "Reading Date" (DATE).
- Charges & Financials: "Electricity Charges" (CURRENCY), "Fuel Price Adjustment" (CURRENCY), "Taxes & Duties" (CURRENCY), "Surcharges" (CURRENCY), "Arrears" (CURRENCY), "Total Amount" / "Amount Within Due Date" (CURRENCY), "Amount After Due Date" (CURRENCY).
- Contact & Location: "Service Address" (ADDRESS), "Support Phone" (PHONE), "Website" (URL), "Support Email" (EMAIL).

--- DOMAIN 2: BANKING & FINANCIAL (STATEMENTS, INVOICES, RECEIPTS, CHEQUES) ---
Look for: "Bank Name" / "Issuer" (ORGANIZATION), "Account Holder" (PERSON_NAME), "Account Number" / "IBAN" (TEXT), "Statement/Invoice Number" (TEXT), "Issue Date" (DATE), "Due Date" (DATE), "Subtotal" (CURRENCY), "Tax/VAT" (CURRENCY), "Total Amount" (CURRENCY), "Payment Status" (TEXT).

--- DOMAIN 3: RETAIL RECEIPTS ---
Look for: "Merchant Name" (ORGANIZATION), "Store Address" (ADDRESS), "Receipt Number" (TEXT), "Date" (DATE), "Subtotal" (CURRENCY), "Tax" (CURRENCY), "Total Paid" (CURRENCY), "Payment Method" (TEXT).

--- DOMAIN 4: CERTIFICATES & ACADEMIC ---
Look for: "Document Title" (TEXT), "Recipient Name" (PERSON_NAME), "Father Name" (PERSON_NAME), "Issue Date" (DATE), "Institution" (ORGANIZATION), "Registration/Roll Number" (TEXT), "Degree/Program" (TEXT), "CGPA/Marks" (TEXT).

--- DOMAIN 5: IDENTITY DOCUMENTS (CNIC, PASSPORT, LICENSE) ---
Look for: "Full Name" (PERSON_NAME), "Father Name" (PERSON_NAME), "Identity Number" / "CNIC" (TEXT), "Date of Birth" (DATE), "Gender" (TEXT), "Issue Date" (DATE), "Expiry Date" (DATE), "Address" (ADDRESS).

--- DOMAIN 6: INSURANCE & WARRANTY ---
Look for: "Provider/Company" (ORGANIZATION), "Policy/Card Holder" (PERSON_NAME), "Policy/Serial Number" (TEXT), "Product Name" (TEXT), "Purchase Date" (DATE), "Effective Date" (DATE), "Expiry Date" (DATE), "Premium Amount" (CURRENCY), "Sum Insured" (CURRENCY).

--- DOMAIN 7: GENERIC / CUSTOM DOCUMENTS ---
Look for: "Title" (TEXT), "Reference Number" (TEXT), "Names" (PERSON_NAME), "Company" (ORGANIZATION), "Dates" (DATE), "Amounts" (CURRENCY), "Address" (ADDRESS), "Phone" (PHONE), "Email" (EMAIL), "Website" (URL).

=== OUTPUT FORMAT ===
Return ONLY valid JSON matching this exact structure:
{
  "category": "Bill",
  "document_type": "Electricity Bill",
  "fields": [
    {"name": "Provider", "value": "LESCO", "type": "ORGANIZATION"},
    {"name": "Consumer Name", "value": "Hamdan Khan", "type": "PERSON_NAME"},
    {"name": "Consumer Number", "value": "12345678901234", "type": "TEXT"},
    {"name": "Reference Number", "value": "04 11234 5678900 U", "type": "TEXT"},
    {"name": "Meter Number", "value": "9876543", "type": "TEXT"},
    {"name": "Billing Month", "value": "September 2026", "type": "TEXT"},
    {"name": "Issue Date", "value": "2026-09-05", "type": "DATE"},
    {"name": "Due Date", "value": "2026-09-20", "type": "DATE"},
    {"name": "Previous Reading", "value": "14250", "type": "NUMBER"},
    {"name": "Current Reading", "value": "14680", "type": "NUMBER"},
    {"name": "Units Consumed", "value": "430", "type": "NUMBER"},
    {"name": "Tariff", "value": "A-1a(01)", "type": "TEXT"},
    {"name": "Electricity Charges", "value": "12900", "type": "CURRENCY"},
    {"name": "Fuel Price Adjustment", "value": "850", "type": "CURRENCY"},
    {"name": "Taxes & Duties", "value": "2150", "type": "CURRENCY"},
    {"name": "Arrears", "value": "0", "type": "CURRENCY"},
    {"name": "Total Amount Within Due Date", "value": "15900", "type": "CURRENCY"},
    {"name": "Amount After Due Date", "value": "17100", "type": "CURRENCY"},
    {"name": "Service Address", "value": "House 12, Street 4, Sector F-8/1, Islamabad", "type": "ADDRESS"}
  ]
}
"""

VALID_TYPES = {
    "TEXT",
    "NUMBER",
    "DATE",
    "CURRENCY",
    "PHONE",
    "EMAIL",
    "URL",
    "ADDRESS",
    "PERSON_NAME",
    "ORGANIZATION",
}

PLACEHOLDER_STRINGS = {"N/A", "UNKNOWN", "NOT SPECIFIED", "NONE", "NULL", "NOT AVAILABLE", "N / A"}


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
      raise ValueError("AI API key is not configured")

    user_context = f"Document Type Context: {document_type or 'UNKNOWN'}\n\nRaw OCR Text:\n{ocr_text}"

    # Support Groq API key (starts with 'gsk_')
    if self.api_key.startswith("gsk_"):
      return await self._extract_groq(user_context)

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

  async def _extract_groq(self, user_context: str) -> ExtractionResponse:
    model = self.model_name if ("groq/" in self.model_name or "llama" in self.model_name or "qwen" in self.model_name) else "groq/compound"
    req = urllib.request.Request(
        'https://api.groq.com/openai/v1/chat/completions',
        headers={
            'Authorization': f'Bearer {self.api_key}',
            'Content-Type': 'application/json',
            'User-Agent': 'Mozilla/5.0'
        },
        data=json.dumps({
            'model': model,
            'messages': [
                {'role': 'system', 'content': SYSTEM_PROMPT},
                {'role': 'user', 'content': user_context}
            ],
            'response_format': {'type': 'json_object'},
            'temperature': 0.1
        }).encode('utf-8')
    )

    def _call_groq():
      with urllib.request.urlopen(req, timeout=60) as response:
        res = json.loads(response.read().decode('utf-8'))
        return res['choices'][0]['message']['content']

    max_retries = 3
    for attempt in range(max_retries):
      try:
        raw_content = await asyncio.to_thread(_call_groq)
        return self._parse_and_validate(raw_content)
      except urllib.error.HTTPError as http_err:
        if http_err.code == 429 and attempt < max_retries - 1:
          wait_time = (attempt + 1) * 3
          logger.warning(f"Groq API rate limit (429). Retrying in {wait_time}s... (attempt {attempt + 1}/{max_retries})")
          await asyncio.sleep(wait_time)
        else:
          err_body = http_err.read().decode('utf-8', errors='ignore') if hasattr(http_err, 'read') else str(http_err)
          logger.error(f"Groq API HTTP Error {http_err.code}: {err_body}")
          if http_err.code == 429:
            raise ValueError("AI API rate limit reached. Please wait a few seconds and try again.")
          raise ValueError(f"AI API error {http_err.code}: {http_err.reason}")
      except (TimeoutError, socket.timeout, urllib.error.URLError) as timeout_err:
        if attempt < max_retries - 1:
          wait_time = (attempt + 1) * 2
          logger.warning(f"Groq API read timeout ({timeout_err}). Retrying in {wait_time}s... (attempt {attempt + 1}/{max_retries})")
          await asyncio.sleep(wait_time)
        else:
          logger.error(f"Groq API timeout error: {timeout_err}")
          raise ValueError("AI request timed out. Please try again.")
      except Exception as err:
        logger.error(f"Groq API error: {err}")
        raise err

  def _parse_and_validate(self, raw_json: str) -> ExtractionResponse:
    clean_json = raw_json.strip()
    if clean_json.startswith("```json"):
      clean_json = clean_json[7:]
    if clean_json.startswith("```"):
      clean_json = clean_json[3:]
    if clean_json.endswith("```"):
      clean_json = clean_json[:-3]
    clean_json = clean_json.strip()

    # Extract JSON object substring between '{' and '}' to ignore any reasoning text
    first_brace = clean_json.find('{')
    last_brace = clean_json.rfind('}')
    if first_brace != -1 and last_brace > first_brace:
      clean_json = clean_json[first_brace:last_brace + 1]

    data = json.loads(clean_json)

    raw_fields = data.get("fields", [])
    valid_fields = []
    seen_names = set()

    for f in raw_fields:
      name = str(f.get("name", "")).strip()
      value = str(f.get("value", "")).strip()
      ftype = str(f.get("type", "TEXT")).upper().strip()

      if ftype not in VALID_TYPES:
        ftype = "TEXT"

      # Reject empty or placeholder values (anti-hallucination)
      if not name or not value or value.upper() in PLACEHOLDER_STRINGS:
        continue

      # Deduplicate identical field names
      name_key = name.lower()
      if name_key in seen_names:
        continue
      seen_names.add(name_key)

      valid_fields.append(ExtractedField(name=name, value=value, type=ftype))

    category = str(data.get("category", "Other")).strip()
    doc_type = str(data.get("document_type", "UNKNOWN")).strip()

    return ExtractionResponse(
        category=category,
        document_type=doc_type,
        fields=valid_fields
    )
