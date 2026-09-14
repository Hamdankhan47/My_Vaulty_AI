from unittest.mock import AsyncMock, patch
from app.core.config import settings
from app.main import app
from app.schemas.extraction import ExtractedField, ExtractionResponse
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
      document_type="ELECTRICITY_BILL",
      fields=[
          ExtractedField(name="Provider", value="IESCO", type="TEXT"),
          ExtractedField(
              name="Reference Number", value="123456789", type="TEXT"
          ),
          ExtractedField(name="Customer Name", value="Hamdan Khan", type="TEXT"),
          ExtractedField(name="Amount", value="8450", type="NUMBER"),
          ExtractedField(name="Due Date", value="2026-09-18", type="DATE"),
      ],
  )
  mock_extractor_cls.return_value = mock_extractor

  sample_ocr = (
      "IESCO ELECTRICITY BILL\nReference No: 123456789\nCustomer Name: Hamdan"
      " Khan\nAmount Payable: Rs. 8,450\nDue Date: 18-09-2026"
  )

  with patch.object(settings, "gemini_api_key", "test_key"):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": None, "ocr_text": sample_ocr},
    )

  assert response.status_code == 200
  data = response.json()
  assert data["document_type"] == "ELECTRICITY_BILL"
  assert len(data["fields"]) == 5
  assert data["fields"][0]["name"] == "Provider"
  assert data["fields"][0]["value"] == "IESCO"
  assert data["fields"][3]["value"] == "8450"
  assert data["fields"][3]["type"] == "NUMBER"
  assert data["fields"][4]["value"] == "2026-09-18"
  assert data["fields"][4]["type"] == "DATE"


@patch("app.api.routes.extraction.GeminiAIExtractor")
def test_extract_missing_fields(mock_extractor_cls):
  mock_extractor = AsyncMock()
  mock_extractor.extract.return_value = ExtractionResponse(
      document_type="ELECTRICITY_BILL",
      fields=[
          ExtractedField(name="Provider", value="IESCO", type="TEXT"),
          ExtractedField(name="Amount", value="8450", type="NUMBER"),
      ],
  )
  mock_extractor_cls.return_value = mock_extractor

  partial_ocr = "IESCO ELECTRICITY BILL\nAmount: 8450"

  with patch.object(settings, "gemini_api_key", "test_key"):
    response = client.post(
        "/api/v1/documents/extract",
        json={"document_type": "ELECTRICITY_BILL", "ocr_text": partial_ocr},
    )

  assert response.status_code == 200
  data = response.json()
  assert data["document_type"] == "ELECTRICITY_BILL"
  assert len(data["fields"]) == 2


@patch("app.api.routes.extraction.GeminiAIExtractor")
def test_extract_unknown_document(mock_extractor_cls):
  mock_extractor = AsyncMock()
  mock_extractor.extract.return_value = ExtractionResponse(
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
