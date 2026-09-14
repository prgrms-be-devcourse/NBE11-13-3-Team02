from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Spring의 jwt.secret과 반드시 동일한 값이어야 서명 검증이 통과한다.
    jwt_secret: str
    jwt_algorithm: str = "HS512"

    spring_base_url: str = "http://localhost:8080"
    spring_timeout_seconds: float = 10.0

    anthropic_api_key: str
    anthropic_model: str = "claude-opus-5"
    # 상한일 뿐 실제 생성한 토큰만 과금된다. 사고 과정이 잘려 답변이 끊기지 않도록 넉넉히 잡는다.
    anthropic_max_tokens: int = 16000
    # 챗봇은 지연시간이 중요해 낮은 effort로 시작한다. 4단계 평가셋으로 라우팅 정확도를
    # 측정한 뒤 medium/high로 올릴지 판단한다.
    anthropic_effort: Literal["low", "medium", "high", "xhigh", "max"] = "low"

    cors_origins: list[str] = ["http://localhost:5173"]


@lru_cache
def get_settings() -> Settings:
    return Settings()
