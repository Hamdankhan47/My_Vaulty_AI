from unittest.mock import AsyncMock, patch
from app.core.config import settings
from app.main import app
from app.schemas.extraction import ExtractedField, ExtractionResponse
from app.services.gemini_extractor import GeminiAIExtractor
from fastapi.testclient import TestClient

client = TestClient(app)


def test_health_check():
  response = client.get("/health")
  assert response.status_code == 200
  data = response.json()
  assert data["status"] == "healthy"


def test_extract_empty_ocr_text():
  response = client.post(
      "/api/v1/documents/extract",
      json={"document_type": None, "ocr_text": "   "},
  )
  assert response.status_code in (400, 422)


def test_extract_missing_api_key():
  with patch.object(settings, "gemini_api_key", ""):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": None, "ocr_text": "Sample OCR"},
    )
    assert response.status_code == 500
    assert "Gemini API key is missing" in response.json()["detail"]


@patch("app.api.routes.extraction.GeminiAIExtractor")
def test_extract_valid_electricity_bill(mock_extractor_cls):
  mock_extractor = AsyncMock()
  mock_extractor.extract.return_value = ExtractionResponse(
      category="Bill",
      document_type="Electricity Bill",
      fields=[
          ExtractedField(name="Provider", value="IESCO", type="ORGANIZATION"),
          ExtractedField(
              name="Reference Number", value="123456789", type="TEXT"
          ),
          ExtractedField(name="Customer Name", value="Hamdan Khan", type="PERSON_NAME"),
          ExtractedField(name="Amount", value="8450", type="CURRENCY"),
          ExtractedField(name="Due Date", value="2026-09-18", type="DATE"),
          ExtractedField(name="Support Phone", value="+92 300 1234567", type="PHONE"),
          ExtractedField(name="Support Email", value="support@iesco.com", type="EMAIL"),
          ExtractedField(name="Website", value="https://iesco.com.pk", type="URL"),
      ],
  )
  mock_extractor_cls.return_value = mock_extractor

  sample_ocr = (
      "IESCO ELECTRICITY BILL\nReference No: 123456789\nCustomer Name: Hamdan"
      " Khan\nAmount Payable: Rs. 8,450\nDue Date: 18-09-2026\nHelp: +92 300 1234567\nEmail: support@iesco.com\nWeb: https://iesco.com.pk"
  )

  with patch.object(settings, "gemini_api_key", "test_key"):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": None, "ocr_text": sample_ocr},
    )

  assert response.status_code == 200
  data = response.json()
  assert data["document_type"] == "Electricity Bill"
  assert len(data["fields"]) == 8
  assert data["fields"][0]["type"] == "ORGANIZATION"
  assert data["fields"][2]["type"] == "PERSON_NAME"
  assert data["fields"][5]["type"] == "PHONE"
  assert data["fields"][6]["type"] == "EMAIL"
  assert data["fields"][7]["type"] == "URL"


def test_parse_and_validate_anti_hallucination_and_filtering():
  extractor = GeminiAIExtractor(api_key="dummy_key")
  sample_json = """
  {
    "category": "Bill",
    "document_type": "Electricity Bill",
    "fields": [
      {"name": "Provider", "value": "LESCO", "type": "ORGANIZATION"},
      {"name": "Meter Number", "value": "N/A", "type": "TEXT"},
      {"name": "Reference Number", "value": "0412345678", "type": "TEXT"},
      {"name": "Consumer Number", "value": "UNKNOWN", "type": "TEXT"},
      {"name": "Units Consumed", "value": "450", "type": "NUMBER"},
      {"name": "Provider", "value": "LESCO Duplicate", "type": "ORGANIZATION"}
    ]
  }
  """
  result = extractor._parse_and_validate(sample_json)
  assert result.category == "Bill"
  assert result.document_type == "Electricity Bill"
  # Placeholders "N/A", "UNKNOWN" and duplicate "Provider" must be filtered out
  field_names = [f.name for f in result.fields]
  assert "Provider" in field_names
  assert "Reference Number" in field_names
  assert "Units Consumed" in field_names
  assert len(result.fields) == 3


def test_parse_and_validate_full_electricity_bill_schema():
  extractor = GeminiAIExtractor(api_key="dummy_key")
  sample_json = """
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
  result = extractor._parse_and_validate(sample_json)
  assert result.category == "Bill"
  assert result.document_type == "Electricity Bill"
  assert len(result.fields) == 19
  field_map = {f.name: f.value for f in result.fields}
  assert field_map["Consumer Name"] == "Hamdan Khan"
  assert field_map["Consumer Number"] == "12345678901234"
  assert field_map["Reference Number"] == "04 11234 5678900 U"
  assert field_map["Meter Number"] == "9876543"
  assert field_map["Units Consumed"] == "430"
  assert field_map["Total Amount Within Due Date"] == "15900"


@patch("app.api.routes.extraction.GeminiAIExtractor")
def test_extract_missing_fields(mock_extractor_cls):
  mock_extractor = AsyncMock()
  mock_extractor.extract.return_value = ExtractionResponse(
      category="Bill",
      document_type="Electricity Bill",
      fields=[
          ExtractedField(name="Provider", value="IESCO", type="TEXT"),
          ExtractedField(name="Amount", value="8450", type="CURRENCY"),
      ],
  )
  mock_extractor_cls.return_value = mock_extractor

  partial_ocr = "IESCO ELECTRICITY BILL\nAmount: 8450"

  with patch.object(settings, "gemini_api_key", "test_key"):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": "Electricity Bill", "ocr_text": partial_ocr},
    )

  assert response.status_code == 200
  data = response.json()
  assert data["document_type"] == "Electricity Bill"
  assert len(data["fields"]) == 2


@patch("app.api.routes.extraction.GeminiAIExtractor")
def test_extract_unknown_document(mock_extractor_cls):
  mock_extractor = AsyncMock()
  mock_extractor.extract.return_value = ExtractionResponse(
      category="Other",
      document_type="UNKNOWN",
      fields=[
          ExtractedField(name="Note", value="Random text", type="TEXT"),
      ],
  )
  mock_extractor_cls.return_value = mock_extractor

  with patch.object(settings, "gemini_api_key", "test_key"):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": None, "ocr_text": "Hello world random document"},
    )

  assert response.status_code == 200
  data = response.json()
  assert data["document_type"] == "UNKNOWN"


@patch("app.api.routes.extraction.GeminiAIExtractor")
def test_extract_ai_failure_returns_502(mock_extractor_cls):
  mock_extractor = AsyncMock()
  mock_extractor.extract.side_effect = Exception("Gemini API connection error")
  mock_extractor_cls.return_value = mock_extractor

  with patch.object(settings, "gemini_api_key", "test_key"):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": None, "ocr_text": "Sample text"},
    )

  assert response.status_code == 502
  assert "AI extraction failed" in response.json()["detail"]
