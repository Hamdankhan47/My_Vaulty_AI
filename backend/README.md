# MyVault AI Backend

FastAPI service for online AI document extraction using Google Gemini.

## Requirements
* Python 3.10+
* Gemini API Key

## Setup & Running

1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

2. Create `.env` file from `.env.example`:
   ```bash
   cp .env.example .env
   ```
   Add your valid `GEMINI_API_KEY` in `.env`.

3. Start the FastAPI server:
   ```bash
   uvicorn app.main:app --reload --port 8000
   ```

4. Access API Documentation:
   * Swagger UI: http://localhost:8000/docs
   * ReDoc: http://localhost:8000/redoc

## Testing
Run pytest:
```bash
pytest
```
