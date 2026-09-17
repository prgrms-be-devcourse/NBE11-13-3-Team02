import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from google import genai
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import chat
from app.config import get_settings
from app.quality import QualityMetrics
from app.rag import FaqIndex
from app.rate_limit import ChatUsageLimiter
from app.router import QuestionRouter
from app.spring_client import SpringClient

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    app.state.spring_client = SpringClient(
        base_url=settings.spring_base_url,
        timeout=settings.spring_timeout_seconds,
    )
    app.state.genai = genai.Client(api_key=settings.gemini_api_key)
    app.state.faq = await FaqIndex.build(app.state.genai)
    # 예시 문장 임베딩은 기동 시 한 번만 만든다. 요청마다 다시 뽑으면 라우팅이
    # 아끼려는 호출보다 더 많은 호출을 쓰게 된다.
    #
    # 여기서 실패해도 기동은 계속한다. 라우팅은 호출을 아끼는 최적화이지 기능이
    # 아니다. 라우터가 없으면 run_agent 가 전부 일반 경로로 흘려보내 라우팅 이전과
    # 똑같이 동작한다. 임베딩 한도(분당 100회, 하루 1000회)에 걸렸다는 이유로
    # 챗봇 전체가 안 뜨는 쪽이 훨씬 나쁘다.
    try:
        app.state.question_router = await QuestionRouter.build(app.state.genai)
    except Exception:
        logging.getLogger(__name__).warning(
            "라우터 예시 임베딩 실패. 라우팅 없이 기동합니다(기존 동작). "
            "원인이 해소되면 재시작하세요.",
            exc_info=True,
        )
        app.state.question_router = None
    app.state.quality = QualityMetrics()
    app.state.chat_usage_limiter = ChatUsageLimiter(
        burst_capacity=settings.chat_burst_capacity,
        refill_per_minute=settings.chat_refill_per_minute,
        daily_message_limit=settings.chat_daily_message_limit,
    )
    yield
    await app.state.spring_client.aclose()


app = FastAPI(title="가치사 챗봇 서버", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=get_settings().cors_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(chat.router)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
