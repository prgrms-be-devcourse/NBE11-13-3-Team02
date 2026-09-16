import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from google import genai
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import chat
from app.config import get_settings
from app.rag import FaqIndex
from app.rate_limit import ChatUsageLimiter
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
