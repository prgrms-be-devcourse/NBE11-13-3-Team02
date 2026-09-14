import asyncio
import json
import logging
import uuid
from collections.abc import AsyncIterator
from typing import Annotated, Literal

from anthropic import AsyncAnthropic
from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.agent import run_agent
from app.config import Settings, get_settings
from app.security import CurrentUserDep
from app.spring_client import SpringClientDep

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat", tags=["chat"])


def get_anthropic_client(request: Request) -> AsyncAnthropic:
    return request.app.state.anthropic


AnthropicDep = Annotated[AsyncAnthropic, Depends(get_anthropic_client)]
SettingsDep = Annotated[Settings, Depends(get_settings)]


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    # 프로젝트의 다른 API와 맞춰 요청/응답 JSON 키를 camelCase로 통일한다.
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    message: str = Field(min_length=1, max_length=2000)
    conversation_id: str | None = None
    # 대화 기록은 클라이언트가 보낸다(서버 무상태). 도구는 언제나 토큰의 주인 데이터만
    # 반환하므로, 기록을 조작해도 남의 정보에는 접근할 수 없다.
    history: list[ChatMessage] = Field(default_factory=list, max_length=20)


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/stream")
async def stream_chat(
    payload: ChatRequest,
    request: Request,
    user: CurrentUserDep,
    spring: SpringClientDep,
    client: AnthropicDep,
    settings: SettingsDep,
) -> StreamingResponse:
    conversation_id = payload.conversation_id or str(uuid.uuid4())

    async def event_stream() -> AsyncIterator[str]:
        yield _sse("start", {"conversationId": conversation_id})
        try:
            agent_events = run_agent(
                client=client,
                settings=settings,
                spring=spring,
                user=user,
                message=payload.message,
                history=[m.model_dump() for m in payload.history],
            )
            async for event, data in agent_events:
                if await request.is_disconnected():
                    logger.info("클라이언트 연결 종료: conversation=%s", conversation_id)
                    return
                yield _sse(event, data)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("응답 생성 실패: conversation=%s", conversation_id)
            yield _sse("error", {"message": "답변을 생성하지 못했습니다."})
            return
        yield _sse("done", {"conversationId": conversation_id})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            # nginx 등 리버스 프록시가 스트림을 버퍼링해 한 번에 뱉는 것을 막는다.
            "X-Accel-Buffering": "no",
        },
    )


@router.get("/upstream-check")
async def upstream_check(user: CurrentUserDep, spring: SpringClientDep) -> dict:
    """사용자 토큰이 Spring까지 그대로 전달되는지 확인하는 배선 점검용 엔드포인트."""
    response = await spring.get("/api/users/me", access_token=user.access_token)
    return {
        "chatbotResolvedUserId": user.user_id,
        "springStatus": response.status_code,
        "springBody": response.json() if response.is_success else None,
    }
