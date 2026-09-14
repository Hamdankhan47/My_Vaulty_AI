from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
  app_name: str = "MyVault AI Backend"
  environment: str = "development"
  gemini_api_key: str = ""
  gemini_model: str = "gemini-2.5-flash"

  model_config = SettingsConfigDict(
      env_file=".env", extra="ignore", case_sensitive=False
  )


settings = Settings()
