from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Spring의 jwt.secret과 반드시 동일한 값이어야 서명 검증이 통과한다.
    jwt_secret: str
    jwt_algorithm: str = "HS512"

    spring_base_url: str = "http://localhost:8080"
    spring_timeout_seconds: float = 10.0

    gemini_api_key: str
    # 무료 티어가 적용되는 모델. 도구 선택 품질이 중요하면 flash, 더 아끼려면 flash-lite.
    gemini_model: str = "gemini-3.8-flash"
    gemini_max_output_tokens: int = 4096

    cors_origins: list[str] = ["http://localhost:5173"]


@lru_cache
def get_settings() -> Settings:
    return Settings()
