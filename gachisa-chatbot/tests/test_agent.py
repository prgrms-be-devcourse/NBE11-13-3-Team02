import json

import httpx
import pytest

from app.agent import MAX_TURNS, run_agent
from app.config import get_settings
from app.rate_limit import ChatUsageLimiter
from app.security import CurrentUser
from app.spring_client import SpringClient
from tests.fakes import FakeFaq, FakeGenai, call_part, call_turn, text_turn, turn

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


async def collect(client, spring, message="안녕", history=None, faq=None, usage_limiter=None):
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
    ):
        events.append((event, data))
    return events


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

    # 텍스트 토큰 뒤에 이번 턴의 사용량을 알리는 usage 이벤트가 하나 따라온다.
    assert [e for e, _ in events] == ["token", "token", "usage"]
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

    assert events[-1][0] == "error"


async def test_도구_루프가_MAX_TURNS를_넘지_않는다(orders_spring):
    client = FakeGenai([call_turn("get_my_orders") for _ in range(MAX_TURNS)])

    events = await collect(client, orders_spring, "무한 루프 유도")

    assert len(client.calls) == MAX_TURNS
    assert events[-1][0] == "error"


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
