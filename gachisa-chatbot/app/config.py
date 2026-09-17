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
    # 무료 티어 기준으로 고른다. 3.8/3.7 flash는 무료 티어에서 503(혼잡)이 잦아 기본에서 뺐다.
    # 더 아끼려면 gemini-3.5-flash-lite.
    gemini_model: str = "gemini-3.5-flash"
    # 임베딩은 생성 모델과 별도 쿼터를 쓴다(무료 티어: 분당 100, 하루 1000).
    # 한도는 모델마다 따로라, 소진되면 이 값을 바꿔 당장 급한 불을 끌 수 있다.
    # 다만 모델이 다르면 벡터 공간이 달라져 임계값과 기준선을 다시 재야 한다.
    gemini_embed_model: str = "gemini-embedding-001"
    gemini_max_output_tokens: int = 4096

    # Gemini 무료 티어는 모델당 분당 5회, 하루 20회를 팀 전체가 나눠 쓴다.
    # 대화 하나가 API를 2~3회 쓰므로, 아래 기본값은 팀원 여럿이 같이 쓸 때
    # 한 사람이 순간적으로 공유 한도를 다 써버리지 않도록 보수적으로 잡은 값이다.
    # 필요하면 .env에서 조정한다.
    chat_burst_capacity: float = 2.0
    chat_refill_per_minute: float = 1.0
    chat_daily_message_limit: int = 4

    cors_origins: list[str] = ["http://localhost:5173"]


@lru_cache
def get_settings() -> Settings:
    return Settings()
