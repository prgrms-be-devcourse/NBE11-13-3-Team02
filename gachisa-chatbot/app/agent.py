import asyncio
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any

from google.genai import errors, types

from app import prompts
from app.config import Settings
from app.quality import QualityMetrics
from app.rate_limit import ChatUsageLimiter
from app.router import QuestionRouter, Route, RouteDecision
from app.security import CurrentUser
from app.spring_client import SpringClient
from app.rag import FaqIndex
from app.tools import TOOL_DEFINITIONS, execute_tool

logger = logging.getLogger(__name__)

# 도구 호출 → 결과 → 재질의 사이클의 상한. 무한 루프와 비용 폭주를 막는다.
MAX_TURNS = 5

# FAQ 경로가 검색 결과를 근거로 인정하는 최저 유사도. 이보다 낮으면 관련 없는
# 문서라 보고 도구를 쓸 수 있는 일반 경로로 되돌린다. 라우터가 FAQ로 보냈어도
# 실제로 답할 근거가 없으면 지어내는 것보다 왕복을 한 번 더 도는 편이 낫다.
FAQ_MIN_RELEVANCE = 0.60

AGENT_PROMPT = prompts.load("agent_system")
SYSTEM_PROMPT = AGENT_PROMPT.text

FAQ_PROMPT = prompts.load("faq_system")
FAQ_SYSTEM_PROMPT = FAQ_PROMPT.text


def _gemini_tools(allowed: tuple[str, ...] | None) -> list[types.Tool] | None:
    """경로가 허용한 도구만 추려 Gemini 형식으로 만든다.

    allowed가 None이면 전체, 빈 튜플이면 도구 없음(None을 반환해 SDK에 도구를
    아예 넘기지 않는다). 도구 정의는 매 호출 프롬프트에 실리므로, 좁히면
    모델이 엉뚱한 도구를 고를 여지와 입력 토큰이 함께 줄어든다.
    """
    definitions = [
        tool for tool in TOOL_DEFINITIONS if allowed is None or tool["name"] in allowed
    ]
    if not definitions:
        return None
    return [
        types.Tool(
            function_declarations=[
                types.FunctionDeclaration(
                    name=tool["name"],
                    description=tool["description"],
                    parameters_json_schema=tool["input_schema"],
                )
                for tool in definitions
            ]
        )
    ]


GEMINI_TOOLS = _gemini_tools(None)


@dataclass
class _TurnResult:
    """생성 호출 한 번의 결과. 제너레이터가 이벤트만 내보내므로 값은 여기 담는다."""

    parts: list[types.Part] = field(default_factory=list)
    failed: bool = False


async def _stream_turn(
    *,
    client: Any,
    settings: Settings,
    config: types.GenerateContentConfig,
    contents: list[types.Content],
    user: CurrentUser,
    usage_limiter: ChatUsageLimiter,
    result: _TurnResult,
) -> AsyncIterator[tuple[str, dict]]:
    """생성 모델을 한 번 호출하고 토큰을 흘려보낸다. 결과 파트는 result에 담는다."""
    received: list[types.Part] = []
    usage = None

    try:
        stream = await client.aio.models.generate_content_stream(
            model=settings.gemini_model, contents=contents, config=config
        )
        async for chunk in stream:
            if chunk.usage_metadata is not None:
                usage = chunk.usage_metadata
            if not chunk.candidates:
                continue
            for part in chunk.candidates[0].content.parts or []:
                received.append(part)
                if part.text:
                    yield "token", {"text": part.text}
    except errors.ClientError as e:
        # 무료 티어는 모델당 분당 5회다. 도구를 쓰면 질문 하나가 2~3회를 소모하므로
        # 연달아 물으면 쉽게 걸린다. 일반 오류와 구분해 안내해야 원인을 알 수 있다.
        if e.code != 429:
            raise
        logger.warning("무료 티어 호출 한도 초과: user=%s", user.user_id)
        yield "error", {
            "message": "무료 사용량 한도에 걸렸습니다. 20초쯤 뒤에 다시 물어봐 주세요."
        }
        result.failed = True
        return

    if usage is not None:
        logger.info(
            "토큰 사용 model=%s in=%s out=%s",
            settings.gemini_model,
            usage.prompt_token_count,
            usage.candidates_token_count,
        )
        daily_input, daily_output = await usage_limiter.record_tokens(
            user.user_id, usage.prompt_token_count, usage.candidates_token_count
        )
        # 위젯이 매 턴 갱신할 수 있도록 이번 턴 값과 오늘 누적치를 함께 보낸다.
        yield "usage", {
            "turnInputTokens": usage.prompt_token_count,
            "turnOutputTokens": usage.candidates_token_count,
            "dailyInputTokens": daily_input,
            "dailyOutputTokens": daily_output,
        }

    result.parts = _merge_text(received)


def build_history(history: list[dict[str, Any]]) -> list[types.Content]:
    # Gemini는 assistant가 아니라 model 역할을 쓴다.
    return [
        types.Content(
            role="model" if item["role"] == "assistant" else "user",
            parts=[types.Part.from_text(text=item["content"])],
        )
        for item in history
    ]


def _merge_text(parts: list[types.Part]) -> list[types.Part]:
    """스트리밍으로 쪼개져 온 텍스트 조각을 하나로 합친다. 함수 호출 파트는 그대로 둔다."""
    merged: list[types.Part] = []
    buffer = ""
    for part in parts:
        if part.function_call is not None:
            if buffer:
                merged.append(types.Part.from_text(text=buffer))
                buffer = ""
            merged.append(part)
        elif part.text:
            buffer += part.text
    if buffer:
        merged.append(types.Part.from_text(text=buffer))
    return merged


def _user_content(message: str, image: tuple[bytes, str] | None) -> types.Content:
    parts = [types.Part.from_text(text=message)]
    if image is not None:
        data, mime_type = image
        # 이미지를 텍스트보다 앞에 둔다. 질문이 이미지를 가리키는 맥락이 되기 때문이다.
        parts.insert(0, types.Part.from_bytes(data=data, mime_type=mime_type))
    return types.Content(role="user", parts=parts)


async def _faq_context(faq: FaqIndex, message: str, decision: RouteDecision) -> str | None:
    """FAQ 경로가 쓸 근거를 모은다. 쓸 만한 게 없으면 None."""
    if decision.vector is not None:
        results = faq.search_with_vector(decision.vector)
    else:
        results = await faq.search(message)

    if not results or results[0]["relevance"] < FAQ_MIN_RELEVANCE:
        return None
    return "\n\n".join(
        f"[{item['title']}]\n{item['content']}"
        for item in results
        if item["relevance"] >= FAQ_MIN_RELEVANCE
    )


async def run_agent(
    *,
    client: Any,
    settings: Settings,
    spring: SpringClient,
    user: CurrentUser,
    faq: FaqIndex,
    usage_limiter: ChatUsageLimiter,
    message: str,
    history: list[dict[str, Any]],
    image: tuple[bytes, str] | None = None,
    router: QuestionRouter | None = None,
    quality: QualityMetrics | None = None,
) -> AsyncIterator[tuple[str, dict]]:
    """질문을 경로로 나눈 뒤 실행한다. (이벤트명, 데이터) 튜플을 스트리밍으로 내보낸다.

    image는 (바이트, MIME 타입). 있으면 사용자 메시지에 함께 싣는다.
    router가 없으면 전부 일반 경로로 흘려보내 기존과 똑같이 동작한다.
    """
    decision = RouteDecision(Route.GENERAL, "no-router")
    if router is not None:
        decision = await router.classify(message, has_image=image is not None)

    context: str | None = None
    if decision.route is Route.FAQ:
        context = await _faq_context(faq, message, decision)
        if context is None:
            # 라우터는 FAQ라고 봤지만 실제로 답할 근거가 없다. 지어내느니
            # 도구를 쓸 수 있는 일반 경로로 되돌린다.
            decision = RouteDecision(
                Route.GENERAL, "faq-miss", decision.score, decision.vector
            )

    logger.info(
        "라우팅 user=%s route=%s reason=%s score=%s",
        user.user_id,
        decision.route,
        decision.reason,
        decision.score,
    )
    yield "route", {
        "route": str(decision.route),
        "reason": decision.reason,
        "score": decision.score,
    }

    # 제너레이터는 값을 돌려줄 수 없어 호출 횟수를 담을 상자를 넘긴다.
    counter = {"calls": 0}
    if decision.route is Route.FAQ:
        async for event in _run_faq_route(
            client=client,
            settings=settings,
            user=user,
            usage_limiter=usage_limiter,
            message=message,
            history=history,
            context=context or "",
        ):
            yield event
        counter["calls"] = 1
    else:
        async for event in _run_tool_loop(
            client=client,
            settings=settings,
            spring=spring,
            user=user,
            faq=faq,
            usage_limiter=usage_limiter,
            message=message,
            history=history,
            image=image,
            decision=decision,
            counter=counter,
        ):
            yield event

    # 라우팅의 효과는 "생성 모델을 몇 번 불렀나"로 드러난다. 무료 티어 한도를
    # 쓰는 것이 이 호출이기 때문이다. 프런트와 시연에서 바로 보이도록 내보낸다.
    # prompts 는 이 답이 어느 프롬프트에서 나왔는지 되짚기 위한 것이다.
    used_prompts = (
        [FAQ_PROMPT.label] if decision.route is Route.FAQ else [AGENT_PROMPT.label]
    )
    yield "metrics", {
        "route": str(decision.route),
        "generationCalls": counter["calls"],
        "prompts": used_prompts,
    }

    if quality is not None:
        await quality.record(
            route=str(decision.route),
            reason=decision.reason,
            generation_calls=counter["calls"],
            prompt_labels=used_prompts,
        )


async def _run_faq_route(
    *,
    client: Any,
    settings: Settings,
    user: CurrentUser,
    usage_limiter: ChatUsageLimiter,
    message: str,
    history: list[dict[str, Any]],
    context: str,
) -> AsyncIterator[tuple[str, dict]]:
    """FAQ 발췌를 미리 넣고 도구 없이 한 번에 답한다. 생성 모델 호출 1회."""
    config = types.GenerateContentConfig(
        system_instruction=FAQ_SYSTEM_PROMPT.format(context=context),
        max_output_tokens=settings.gemini_max_output_tokens,
    )
    contents = [*build_history(history), _user_content(message, None)]

    result = _TurnResult()
    async for event in _stream_turn(
        client=client,
        settings=settings,
        config=config,
        contents=contents,
        user=user,
        usage_limiter=usage_limiter,
        result=result,
    ):
        yield event

    if result.failed:
        return
    if not result.parts:
        logger.warning("FAQ 경로에서 빈 응답: user=%s", user.user_id)
        yield "error", {"message": "이 요청에는 답변할 수 없습니다."}


async def _run_tool_loop(
    *,
    client: Any,
    settings: Settings,
    spring: SpringClient,
    user: CurrentUser,
    faq: FaqIndex,
    usage_limiter: ChatUsageLimiter,
    message: str,
    history: list[dict[str, Any]],
    image: tuple[bytes, str] | None,
    decision: RouteDecision,
    counter: dict[str, int],
) -> AsyncIterator[tuple[str, dict]]:
    """도구 호출 → 결과 → 재질의 루프. 경로가 허용한 도구만 노출한다."""
    contents = [*build_history(history), _user_content(message, image)]
    config = types.GenerateContentConfig(
        system_instruction=SYSTEM_PROMPT,
        tools=_gemini_tools(decision.tools),
        max_output_tokens=settings.gemini_max_output_tokens,
        # 도구는 이 파일의 루프가 직접 실행한다. SDK가 대신 호출하려 들면 안 된다.
        automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
    )

    for _ in range(MAX_TURNS):
        result = _TurnResult()
        async for event in _stream_turn(
            client=client,
            settings=settings,
            config=config,
            contents=contents,
            user=user,
            usage_limiter=usage_limiter,
            result=result,
        ):
            yield event
        counter["calls"] += 1

        if result.failed:
            return
        if not result.parts:
            logger.warning("모델이 빈 응답을 반환했습니다 (차단 가능성): user=%s", user.user_id)
            yield "error", {"message": "이 요청에는 답변할 수 없습니다."}
            return

        contents.append(types.Content(role="model", parts=result.parts))

        calls = [p.function_call for p in result.parts if p.function_call is not None]
        if not calls:
            return

        for call in calls:
            yield "tool", {"name": call.name}

        # 도구를 순차가 아니라 동시에 실행한다. 주문 조회와 배송 조회가 함께 필요할 때
        # 대기 시간이 합이 아니라 최댓값이 된다.
        results = await asyncio.gather(
            *(execute_tool(c.name, dict(c.args or {}), spring, user, faq) for c in calls)
        )

        contents.append(
            types.Content(
                role="user",
                parts=[
                    types.Part.from_function_response(name=call.name, response=result_item)
                    for call, result_item in zip(calls, results, strict=True)
                ],
            )
        )

    logger.warning("MAX_TURNS 도달: user=%s", user.user_id)
    yield "error", {"message": "요청이 복잡해 답변을 완성하지 못했습니다. 조금 더 구체적으로 물어봐 주세요."}
