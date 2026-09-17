import asyncio
import base64
import binascii
import json
import logging
import uuid
from collections.abc import AsyncIterator
from typing import Annotated, Literal

from google import genai
from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel

from app.agent import run_agent
from app.config import Settings, get_settings
from app.rag import FaqIndex
from app import prompts
from app.quality import QualityMetrics
from app.rate_limit import ChatUsageLimiter, DailyBudgetExceeded, RateLimitExceeded
from app.router import QuestionRouter
from app.security import CurrentUser, CurrentUserDep
from app.spring_client import SpringClientDep

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat", tags=["chat"])


def get_genai_client(request: Request) -> genai.Client:
    return request.app.state.genai


GenaiDep = Annotated[genai.Client, Depends(get_genai_client)]


def get_faq_index(request: Request) -> FaqIndex:
    return request.app.state.faq


FaqDep = Annotated[FaqIndex, Depends(get_faq_index)]


def get_question_router(request: Request) -> QuestionRouter:
    return request.app.state.question_router


QuestionRouterDep = Annotated[QuestionRouter, Depends(get_question_router)]


def get_quality(request: Request) -> QualityMetrics:
    return request.app.state.quality


QualityDep = Annotated[QualityMetrics, Depends(get_quality)]
SettingsDep = Annotated[Settings, Depends(get_settings)]


def get_usage_limiter(request: Request) -> ChatUsageLimiter:
    return request.app.state.chat_usage_limiter


UsageLimiterDep = Annotated[ChatUsageLimiter, Depends(get_usage_limiter)]


async def enforce_rate_limit(user: CurrentUserDep, limiter: UsageLimiterDep) -> None:
    """도구 호출을 시작하기 전에 사용량을 확인한다.

    인증(401)과 같은 자리 — 실패하면 SSE 스트림을 열지도 않고 바로 HTTP 오류로
    끝낸다. 스트림을 연 뒤에 막으면 이미 연결 자원을 쓴 셈이라 의미가 없다.
    """
    try:
        await limiter.check(user.user_id, user.name)
    except RateLimitExceeded as e:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS,
            "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.",
            headers={"Retry-After": str(max(1, round(e.retry_after_seconds)))},
        ) from e
    except DailyBudgetExceeded as e:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS,
            "오늘 사용할 수 있는 대화 횟수를 모두 사용했습니다. 내일 다시 이용해 주세요.",
        ) from e


RateLimitDep = Annotated[None, Depends(enforce_rate_limit)]

ADMIN_ROLE = "ROLE_ADMIN"


def require_admin(user: CurrentUserDep) -> CurrentUser:
    """관리자 전용 엔드포인트의 문지기.

    role 클레임은 core가 서명한 토큰에서 나오므로 시크릿 없이는 위조할 수 없다.
    다만 권한을 회수해도 이미 발급된 토큰은 만료 전까지 유효하다 — core의 관리자
    API와 같은 조건이라 여기만 더 엄격하게 만들 이유는 없다.
    """
    if user.role != ADMIN_ROLE:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "관리자만 조회할 수 있습니다.")
    return user


AdminUserDep = Annotated[CurrentUser, Depends(require_admin)]


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGE_BYTES = 4 * 1024 * 1024


class ChatImage(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    mime_type: str
    data: str  # base64

    @field_validator("mime_type")
    @classmethod
    def _allowed_type(cls, value: str) -> str:
        if value not in ALLOWED_IMAGE_TYPES:
            raise ValueError(f"지원하지 않는 이미지 형식입니다: {value}")
        return value

    def decode(self) -> bytes:
        try:
            raw = base64.b64decode(self.data, validate=True)
        except binascii.Error as e:
            raise ValueError("이미지 디코딩에 실패했습니다.") from e
        if len(raw) > MAX_IMAGE_BYTES:
            raise ValueError("이미지가 너무 큽니다(최대 4MB).")
        return raw


class ChatRequest(BaseModel):
    # 프로젝트의 다른 API와 맞춰 요청/응답 JSON 키를 camelCase로 통일한다.
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    message: str = Field(min_length=1, max_length=2000)
    conversation_id: str | None = None
    # 대화 기록은 클라이언트가 보낸다(서버 무상태). 도구는 언제나 토큰의 주인 데이터만
    # 반환하므로, 기록을 조작해도 남의 정보에는 접근할 수 없다.
    history: list[ChatMessage] = Field(default_factory=list, max_length=20)
    # 업로드는 신뢰 경계다. 형식과 크기를 서버에서 검증한다.
    image: ChatImage | None = None


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/stream")
async def stream_chat(
    payload: ChatRequest,
    request: Request,
    user: CurrentUserDep,
    spring: SpringClientDep,
    client: GenaiDep,
    faq: FaqDep,
    question_router: QuestionRouterDep,
    quality: QualityDep,
    settings: SettingsDep,
    limiter: UsageLimiterDep,
    _rate_limit: RateLimitDep,
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
                faq=faq,
                usage_limiter=limiter,
                message=payload.message,
                history=[m.model_dump() for m in payload.history],
                image=(payload.image.decode(), payload.image.mime_type) if payload.image else None,
                router=question_router,
                quality=quality,
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


class UsageResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    daily_messages_used: int
    daily_message_limit: int
    daily_input_tokens: int
    daily_output_tokens: int
    burst_tokens_available: float
    burst_capacity: float


@router.get("/usage")
async def get_usage(user: CurrentUserDep, limiter: UsageLimiterDep) -> UsageResponse:
    """본인의 오늘 챗봇 사용량을 조회한다. 아무것도 소비하지 않는다."""
    snapshot = await limiter.usage_of(user.user_id)
    return UsageResponse(
        daily_messages_used=snapshot.daily_messages_used,
        daily_message_limit=snapshot.daily_message_limit,
        daily_input_tokens=snapshot.daily_input_tokens,
        daily_output_tokens=snapshot.daily_output_tokens,
        burst_tokens_available=round(snapshot.burst_tokens_available, 2),
        burst_capacity=snapshot.burst_capacity,
    )


class AdminUsageRow(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    user_id: int
    name: str
    daily_messages_used: int
    daily_input_tokens: int
    daily_output_tokens: int
    total_messages_used: int
    total_input_tokens: int
    total_output_tokens: int


class AdminUsageResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    daily_message_limit: int
    total_input_tokens: int
    total_output_tokens: int
    users: list[AdminUsageRow]


@router.get("/admin/usage")
async def get_admin_usage(
    _admin: AdminUserDep, limiter: UsageLimiterDep
) -> AdminUsageResponse:
    """사용자별 챗봇 토큰 사용량을 모아 본다(관리자 전용).

    집계는 챗봇 프로세스 메모리에 있어 서버를 재시작하면 0부터 다시 쌓인다.
    장기 보관이 필요해지면 저장소를 붙여야 한다.
    """
    rows = await limiter.all_usage()
    return AdminUsageResponse(
        daily_message_limit=limiter.daily_message_limit,
        total_input_tokens=sum(row.total_input_tokens for row in rows),
        total_output_tokens=sum(row.total_output_tokens for row in rows),
        users=[
            AdminUsageRow(
                user_id=row.user_id,
                name=row.name,
                daily_messages_used=row.daily_messages_used,
                daily_input_tokens=row.daily_input_tokens,
                daily_output_tokens=row.daily_output_tokens,
                total_messages_used=row.total_messages_used,
                total_input_tokens=row.total_input_tokens,
                total_output_tokens=row.total_output_tokens,
            )
            for row in rows
        ],
    )


class QualityResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    total_messages: int
    routes: dict[str, int]
    reasons: dict[str, int]
    faq_miss: int
    generation_calls: int
    calls_per_message: float
    faq_ratio: float
    prompts: list[str]


@router.get("/admin/quality")
async def get_quality_metrics(
    _admin: AdminUserDep, quality: QualityDep
) -> QualityResponse:
    """라우팅 품질 지표를 본다(관리자 전용).

    평가셋은 배포 전에 재는 도구다. 실제 질문이 평가셋과 다르게 생겼는지, 라우팅이
    운영에서도 기대대로 도는지는 여기서만 보인다. 특히 볼 것:

      faqRatio         호출을 아낀 비율. 평가셋에서는 24%였다. 크게 낮으면 실제
                       질문 분포가 평가셋과 다르다는 뜻이다
      reasons          low-score 가 늘면 예시가 실제 질문을 못 따라가고 있다
      faqMiss          FAQ 로 보냈는데 근거가 없던 횟수. 높으면 FAQ 문서가 부족하다
      callsPerMessage  무료 한도와 직결된다
      prompts          이 숫자들이 어느 프롬프트에서 나왔는지

    집계는 프로세스 메모리에 있어 재시작하면 0부터 다시 쌓인다.
    """
    snapshot = await quality.snapshot()
    return QualityResponse(
        total_messages=snapshot.total_messages,
        routes=snapshot.routes,
        reasons=snapshot.reasons,
        faq_miss=snapshot.faq_miss,
        generation_calls=snapshot.generation_calls,
        calls_per_message=round(snapshot.calls_per_message, 2),
        faq_ratio=round(snapshot.faq_ratio, 3),
        prompts=snapshot.prompts,
    )


class PromptInfo(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    name: str
    version: str
    digest: str
    changelog: str


@router.get("/admin/prompts")
async def get_prompts(_admin: AdminUserDep) -> list[PromptInfo]:
    """지금 돌고 있는 프롬프트의 버전과 digest(관리자 전용).

    운영에서 이상한 답을 발견했을 때, 그 답이 어느 프롬프트에서 나왔는지 대조하는
    용도다. digest 는 파일 내용의 해시라 손댈 수 없다 — 누군가 프롬프트를 고치고
    버전을 안 올렸어도 digest 는 달라진다.
    """
    return [
        PromptInfo(
            name=p.name,
            version=p.version,
            digest=p.digest,
            changelog=prompts.CHANGELOG.get((p.name, p.version), ""),
        )
        for p in prompts.all_prompts()
    ]
