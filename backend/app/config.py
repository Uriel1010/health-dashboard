from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = Field(
        default="postgresql+psycopg://health:configure-locally@db:5432/health_dashboard",
        alias="DATABASE_URL",
    )
    ingest_api_token: str = Field(default="YOUR_INGEST_TOKEN", alias="INGEST_API_TOKEN")
    admin_token: str | None = Field(default=None, alias="ADMIN_TOKEN")
    app_timezone: str = Field(default="Asia/Jerusalem", alias="APP_TIMEZONE")
    session_cookie_name: str = Field(default="health_dashboard_session", alias="SESSION_COOKIE_NAME")
    session_cookie_secure: bool = Field(default=False, alias="SESSION_COOKIE_SECURE")
    session_days: int = Field(default=30, alias="SESSION_DAYS")
    cors_allowed_origins: str = Field(
        default="http://localhost:3016,http://127.0.0.1:3016",
        alias="CORS_ALLOWED_ORIGINS",
    )

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_allowed_origins.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
