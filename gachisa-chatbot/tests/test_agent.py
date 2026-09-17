import json

import httpx
import pytest

from app.agent import FAQ_PROMPT, MAX_TURNS, run_agent
from app.config import get_settings
from app.rate_limit import ChatUsageLimiter
from app.router import Route, RouteDecision
from app.security import CurrentUser
from app.spring_client import SpringClient
from tests.fakes import (
    FakeFaq,
    FakeGenai,
    FakeRouter,
    call_part,
    call_turn,
    text_turn,
    turn,
)

USER = CurrentUser(user_id=7, name="안세호", role="ROLE_BUYER", access_token="tok-abc")


def spring_stub(handler) -> SpringClient:
    return SpringClient(
        base_url="http://spring.test",
        timeout=5.0,
        transport=httpx.MockTransport(handler),
    )


def _generous_limiter() -> ChatUsageLimiter:
    """라우팅/도구 동작을 보는 테스트가 사용량 제한에 걸리지 않게 넉넉히 둔다."""
    return ChatUsageLimiter(burst_capacity=1000, refill_per_minute=1000, daily_message_limit=1000)


async def collect(
    client, spring, message="안녕", history=None, faq=None, usage_limiter=None, router=None
):
    events = []
    async for event, data in run_agent(
        client=client,
        settings=get_settings(),
        spring=spring,
        user=USER,
        faq=faq or FakeFaq(),
        usage_limiter=usage_limiter or _generous_limiter(),
        message=message,
        history=history or [],
        router=router,
    ):
        events.append((event, data))
    return events


def declared_tools(call) -> list[str]:
    """한 API 호출의 config에 실제로 실린 도구 이름들."""
    if not call["config"].tools:
        return []
    return [d.name for tool in call["config"].tools for d in tool.function_declarations]


def function_responses(call):
    """한 API 호출의 contents 마지막 항목에서 function_response 파트를 꺼낸다."""
    return [p.function_response for p in call["contents"][-1].parts if p.function_response]


@pytest.fixture
def orders_spring():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/api/orders":
            return httpx.Response(
                200,
                json={
                    "content": [
                        {
                            "orderId": 11,
                            "orderNumber": "ORD-1",
                            "productName": "무선 이어폰",
                            "quantity": 1,
                            "amount": 49000,
                            "deliveryStatus": "SHIPPING",
                            "createdAt": "2026-09-01T10:00:00",
                        }
                    ]
                },
            )
        if request.url.path == "/api/orders/11/delivery":
            return httpx.Response(
                200,
                json={
                    "orderNumber": "ORD-1",
                    "productName": "무선 이어폰",
                    "deliveryStatus": "SHIPPING",
                    "carrier": "한진택배",
                    "trackingNumber": "1234567890",
                    "recipientName": "안세호",
                    "recipientPhone": "010-1234-5678",
                    "zipCode": "06234",
                    "address": "서울시 강남구 테헤란로 1",
                    "addressDetail": "101동 1203호",
                    "deliveryRequest": "부재시 경비실",
                    "shippingStartedAt": "2026-09-10T09:00:00",
                    "expectedDeliveryAt": "2026-09-12T09:00:00",
                    "deliveredAt": None,
                },
            )
        return httpx.Response(404)

    return spring_stub(handler)


async def test_도구가_필요없으면_텍스트만_스트리밍한다(orders_spring):
    client = FakeGenai([text_turn("안녕", "하세요")])

    events = await collect(client, orders_spring)

    # 어느 경로로 갔는지 알리는 route 로 열고, 텍스트 토큰과 이번 턴의 사용량이
    # 이어지며, 생성 모델을 몇 번 불렀는지 알리는 metrics 로 닫는다.
    assert [e for e, _ in events] == ["route", "token", "token", "usage", "metrics"]
    assert "".join(d["text"] for e, d in events if e == "token") == "안녕하세요"


async def test_도구_결과가_다음_요청에_function_response로_실린다(orders_spring):
    client = FakeGenai([call_turn("get_my_orders"), text_turn("배송 중입니다")])

    events = await collect(client, orders_spring, "내 주문 어디까지 왔어?")

    assert ("tool", {"name": "get_my_orders"}) in events

    responses = function_responses(client.calls[1])
    assert len(responses) == 1
    assert responses[0].name == "get_my_orders"
    assert "무선 이어폰" in json.dumps(responses[0].response, ensure_ascii=False)


async def test_같은_턴의_여러_도구는_한_메시지로_모아_보낸다(orders_spring):
    client = FakeGenai(
        [
            turn(
                [
                    call_part("get_my_orders", {}),
                    call_part("get_order_delivery", {"order_id": 11}),
                ]
            ),
            text_turn("완료"),
        ]
    )

    await collect(client, orders_spring, "주문이랑 배송 알려줘")

    responses = function_responses(client.calls[1])
    assert [r.name for r in responses] == ["get_my_orders", "get_order_delivery"]


async def test_배송조회는_연락처와_상세주소를_모델에_보내지_않는다(orders_spring):
    client = FakeGenai(
        [call_turn("get_order_delivery", {"order_id": 11}), text_turn("배송 중")]
    )

    await collect(client, orders_spring, "배송 조회")

    payload = json.dumps(function_responses(client.calls[1])[0].response, ensure_ascii=False)
    assert "1234567890" in payload  # 운송장은 필요하다
    assert "010-1234-5678" not in payload
    assert "101동 1203호" not in payload
    assert "테헤란로" not in payload


async def test_도구가_실패하면_error_키로_모델에_알린다():
    spring = spring_stub(lambda request: httpx.Response(403, json={"message": "forbidden"}))
    client = FakeGenai([call_turn("get_my_orders"), text_turn("조회 실패")])

    await collect(client, spring, "내 주문")

    response = function_responses(client.calls[1])[0].response
    assert "권한" in response["error"]


async def test_빈_응답이면_error_이벤트로_끝난다(orders_spring):
    client = FakeGenai([turn([])])

    events = await collect(client, orders_spring)

    # metrics 는 실패했을 때도 마지막에 붙는다. 호출은 이미 소비됐기 때문이다.
    assert events[-2][0] == "error"
    assert events[-1][0] == "metrics"


async def test_도구_루프가_MAX_TURNS를_넘지_않는다(orders_spring):
    client = FakeGenai([call_turn("get_my_orders") for _ in range(MAX_TURNS)])

    events = await collect(client, orders_spring, "무한 루프 유도")

    assert len(client.calls) == MAX_TURNS
    assert events[-2][0] == "error"
    assert events[-1][1]["generationCalls"] == MAX_TURNS


async def test_대화_기록은_model_역할로_변환된다(orders_spring):
    client = FakeGenai([text_turn("네")])

    await collect(
        client,
        orders_spring,
        "그거 얼마야?",
        history=[
            {"role": "user", "content": "이어폰 찾아줘"},
            {"role": "assistant", "content": "무선 이어폰이 있습니다"},
        ],
    )

    contents = client.calls[0]["contents"]
    assert [c.role for c in contents] == ["user", "model", "user"]
    assert contents[-1].parts[0].text == "그거 얼마야?"


async def test_검색_도구는_할인가_필터를_spring_쿼리로_넘긴다():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen.update(dict(request.url.params))
        return httpx.Response(200, json={"result": {"content": []}})

    client = FakeGenai(
        [
            call_turn(
                "search_group_buys",
                {"keyword": "노트북", "max_price": 500000, "status": "RECRUITING"},
            ),
            text_turn("없어요"),
        ]
    )

    await collect(client, spring_stub(handler), "50만원 이하 노트북 공구 있어?")

    assert seen["keyword"] == "노트북"
    assert seen["maxPrice"] == "500000"
    assert seen["status"] == "RECRUITING"
    assert "minPrice" not in seen  # None인 필터는 쿼리에서 빠져야 한다


async def test_usage_이벤트가_턴마다_누적치를_함께_보낸다(orders_spring):
    """도구 호출로 두 턴이 돌면 usage 이벤트도 두 번 나가고, 오늘 누적치가 쌓여야 한다."""
    client = FakeGenai([call_turn("get_my_orders"), text_turn("배송 중")])
    limiter = ChatUsageLimiter(burst_capacity=1000, refill_per_minute=1000, daily_message_limit=1000)

    events = await collect(client, orders_spring, "내 주문", usage_limiter=limiter)

    usage_events = [d for e, d in events if e == "usage"]
    assert len(usage_events) == 2
    assert usage_events[0]["turnInputTokens"] == 100
    assert usage_events[0]["dailyInputTokens"] == 100
    assert usage_events[1]["dailyInputTokens"] == 200  # 두 턴 누적
    assert usage_events[1]["dailyOutputTokens"] == 40


async def test_요청에_모델과_도구_선언이_실린다(orders_spring):
    client = FakeGenai([text_turn("네")])

    await collect(client, orders_spring)

    call = client.calls[0]
    assert call["model"] == "gemini-3.5-flash"
    declared = {f.name for t in call["config"].tools for f in t.function_declarations}
    assert declared == {"search_faq", "search_group_buys", "get_my_orders", "get_order_delivery"}
    assert "가치사" in call["config"].system_instruction


# --- 라우팅 ---------------------------------------------------------------
#
# 라우팅의 목적은 생성 모델 호출을 줄이는 것이다. 무료 티어 한도를 쓰는 것이
# 이 호출이므로, 아래 테스트들은 "몇 번 불렀나"를 직접 확인한다.


def faq_router(score: float = 0.82) -> FakeRouter:
    return FakeRouter(RouteDecision(Route.FAQ, "embedding", score))


async def test_FAQ_경로는_생성_모델을_한_번만_부른다(orders_spring):
    client = FakeGenai([text_turn("환불은 모집 마감 전까지 가능합니다.")])

    events = await collect(
        client, orders_spring, "환불 규정이 어떻게 돼?", router=faq_router()
    )

    # 기존 경로라면 "도구 고르기" + "답변 만들기"로 2회가 필요하다.
    assert len(client.calls) == 1
    name, data = events[-1]
    assert name == "metrics"
    assert data["route"] == "faq"
    assert data["generationCalls"] == 1
    # 이 답이 어느 프롬프트에서 나왔는지 되짚을 수 있어야 한다.
    assert data["prompts"] == [FAQ_PROMPT.label]


async def test_FAQ_경로는_도구를_아예_싣지_않는다(orders_spring):
    client = FakeGenai([text_turn("네")])

    await collect(client, orders_spring, "환불 규정", router=faq_router())

    assert declared_tools(client.calls[0]) == []


async def test_FAQ_경로는_검색_결과를_시스템_프롬프트에_넣는다(orders_spring):
    faq = FakeFaq([{"title": "환불 안내 > 언제 되나요", "content": "모집 마감 전까지 가능합니다.", "relevance": 0.9}])
    client = FakeGenai([text_turn("네")])

    await collect(client, orders_spring, "환불 규정", faq=faq, router=faq_router())

    instruction = client.calls[0]["config"].system_instruction
    assert "모집 마감 전까지 가능합니다." in instruction
    assert "환불 안내 > 언제 되나요" in instruction


async def test_근거가_약하면_FAQ_경로를_포기하고_도구_경로로_돌아간다(orders_spring):
    # relevance가 임계값 아래다. 이 발췌로 답하면 지어내는 셈이 된다.
    faq = FakeFaq([{"title": "배송 안내", "content": "관련 없는 내용", "relevance": 0.12}])
    client = FakeGenai([call_turn("search_faq", {"query": "환불"}), text_turn("확인해 보니...")])

    events = await collect(
        client, orders_spring, "환불 규정", faq=faq, router=faq_router()
    )

    route_event = next(data for name, data in events if name == "route")
    assert route_event["route"] == "general"
    assert route_event["reason"] == "faq-miss"
    # 도구를 쓸 수 있는 경로로 돌아왔으므로 도구가 실려 있어야 한다.
    assert declared_tools(client.calls[0])


async def test_ORDER_경로는_주문_도구만_연다(orders_spring):
    client = FakeGenai([call_turn("get_my_orders"), text_turn("배송 중입니다")])
    router = FakeRouter(RouteDecision(Route.ORDER, "rule:personal"))

    await collect(client, orders_spring, "내 주문 어디까지 왔어", router=router)

    tools = declared_tools(client.calls[0])
    assert set(tools) == {"get_my_orders", "get_order_delivery", "search_faq"}
    assert "search_group_buys" not in tools


async def test_라우터가_없으면_기존과_똑같이_전체_도구를_연다(orders_spring):
    client = FakeGenai([text_turn("네")])

    events = await collect(client, orders_spring, "아무 말")

    assert len(declared_tools(client.calls[0])) == 4
    route_event = next(data for name, data in events if name == "route")
    assert route_event["route"] == "general"


async def test_FAQ_경로가_라우터_벡터를_재사용해_임베딩을_아낀다(orders_spring):
    faq = FakeFaq()
    client = FakeGenai([text_turn("네")])
    router = FakeRouter(RouteDecision(Route.FAQ, "embedding", 0.8, vector=[0.1] * 8))

    await collect(client, orders_spring, "환불 규정", faq=faq, router=router)

    # 문자열이 아니라 벡터로 검색했다면 다시 임베딩하지 않은 것이다.
    assert faq.queries == ["<vector:8>"]
