import json

import httpx
import pytest

from app.agent import MAX_TURNS, run_agent
from app.config import get_settings
from app.security import CurrentUser
from app.spring_client import SpringClient
from tests.fakes import (
    FakeAnthropic,
    FakeStream,
    final_message,
    text_block,
    text_delta,
    tool_use_block,
)

USER = CurrentUser(user_id=7, name="안세호", role="USER", access_token="tok-abc")


def spring_stub(handler) -> SpringClient:
    return SpringClient(
        base_url="http://spring.test",
        timeout=5.0,
        transport=httpx.MockTransport(handler),
    )


async def collect(client, spring, message="안녕", history=None):
    events = []
    async for event, data in run_agent(
        client=client,
        settings=get_settings(),
        spring=spring,
        user=USER,
        message=message,
        history=history or [],
    ):
        events.append((event, data))
    return events


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
    client = FakeAnthropic(
        [
            FakeStream(
                [text_delta("안녕"), text_delta("하세요")],
                final_message([text_block("안녕하세요")], "end_turn"),
            )
        ]
    )

    events = await collect(client, orders_spring)

    assert [e for e, _ in events] == ["token", "token"]
    assert "".join(d["text"] for _, d in events) == "안녕하세요"


async def test_도구_결과가_다음_요청에_tool_result로_실린다(orders_spring):
    client = FakeAnthropic(
        [
            FakeStream(
                [],
                final_message(
                    [tool_use_block("tu_1", "get_my_orders", {})], "tool_use"
                ),
            ),
            FakeStream(
                [text_delta("배송 중입니다")],
                final_message([text_block("배송 중입니다")], "end_turn"),
            ),
        ]
    )

    events = await collect(client, orders_spring, "내 주문 어디까지 왔어?")

    assert ("tool", {"name": "get_my_orders"}) in events

    second_request_messages = client.calls[1]["messages"]
    tool_result = second_request_messages[-1]["content"][0]
    assert tool_result["type"] == "tool_result"
    assert tool_result["tool_use_id"] == "tu_1"
    assert tool_result["is_error"] is False
    assert "무선 이어폰" in tool_result["content"]


async def test_같은_턴의_여러_도구는_한_메시지로_모아_보낸다(orders_spring):
    client = FakeAnthropic(
        [
            FakeStream(
                [],
                final_message(
                    [
                        tool_use_block("tu_1", "get_my_orders", {}),
                        tool_use_block("tu_2", "get_order_delivery", {"order_id": 11}),
                    ],
                    "tool_use",
                ),
            ),
            FakeStream(
                [text_delta("완료")], final_message([text_block("완료")], "end_turn")
            ),
        ]
    )

    await collect(client, orders_spring, "주문이랑 배송 알려줘")

    results = client.calls[1]["messages"][-1]["content"]
    assert len(results) == 2
    assert [r["tool_use_id"] for r in results] == ["tu_1", "tu_2"]


async def test_배송조회는_연락처와_상세주소를_모델에_보내지_않는다(orders_spring):
    client = FakeAnthropic(
        [
            FakeStream(
                [],
                final_message(
                    [tool_use_block("tu_1", "get_order_delivery", {"order_id": 11})],
                    "tool_use",
                ),
            ),
            FakeStream(
                [text_delta("배송 중")], final_message([text_block("배송 중")], "end_turn")
            ),
        ]
    )

    await collect(client, orders_spring, "배송 조회")

    payload = client.calls[1]["messages"][-1]["content"][0]["content"]
    assert "1234567890" in payload  # 운송장은 필요하다
    assert "010-1234-5678" not in payload
    assert "101동 1203호" not in payload
    assert "테헤란로" not in payload


async def test_도구가_실패하면_is_error로_모델에_알린다():
    spring = spring_stub(lambda request: httpx.Response(403, json={"message": "forbidden"}))
    client = FakeAnthropic(
        [
            FakeStream(
                [],
                final_message(
                    [tool_use_block("tu_1", "get_my_orders", {})], "tool_use"
                ),
            ),
            FakeStream(
                [text_delta("조회 실패")],
                final_message([text_block("조회 실패")], "end_turn"),
            ),
        ]
    )

    await collect(client, spring, "내 주문")

    tool_result = client.calls[1]["messages"][-1]["content"][0]
    assert tool_result["is_error"] is True
    assert "권한" in tool_result["content"]


async def test_모델이_거부하면_error_이벤트로_끝난다(orders_spring):
    client = FakeAnthropic([FakeStream([], final_message([], "refusal"))])

    events = await collect(client, orders_spring)

    assert events[-1][0] == "error"


async def test_도구_루프가_MAX_TURNS를_넘지_않는다(orders_spring):
    client = FakeAnthropic(
        [
            FakeStream(
                [],
                final_message(
                    [tool_use_block(f"tu_{i}", "get_my_orders", {})], "tool_use"
                ),
            )
            for i in range(MAX_TURNS)
        ]
    )

    events = await collect(client, orders_spring, "무한 루프 유도")

    assert len(client.calls) == MAX_TURNS
    assert events[-1][0] == "error"


async def test_대화_기록이_요청_앞에_붙는다(orders_spring):
    client = FakeAnthropic(
        [FakeStream([text_delta("네")], final_message([text_block("네")], "end_turn"))]
    )

    await collect(
        client,
        orders_spring,
        "그거 얼마야?",
        history=[
            {"role": "user", "content": "이어폰 찾아줘"},
            {"role": "assistant", "content": "무선 이어폰이 있습니다"},
        ],
    )

    messages = client.calls[0]["messages"]
    assert [m["role"] for m in messages] == ["user", "assistant", "user"]
    assert messages[-1]["content"] == "그거 얼마야?"


async def test_검색_도구는_할인가_필터를_spring_쿼리로_넘긴다():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen.update(dict(request.url.params))
        return httpx.Response(200, json={"result": {"content": []}})

    client = FakeAnthropic(
        [
            FakeStream(
                [],
                final_message(
                    [
                        tool_use_block(
                            "tu_1",
                            "search_group_buys",
                            {"keyword": "노트북", "max_price": 500000, "status": "RECRUITING"},
                        )
                    ],
                    "tool_use",
                ),
            ),
            FakeStream(
                [text_delta("없어요")], final_message([text_block("없어요")], "end_turn")
            ),
        ]
    )

    await collect(client, spring_stub(handler), "50만원 이하 노트북 공구 있어?")

    assert seen["keyword"] == "노트북"
    assert seen["maxPrice"] == "500000"
    assert seen["status"] == "RECRUITING"
    assert "minPrice" not in seen  # None인 필터는 쿼리에서 빠져야 한다


async def test_요청에_현재_모델과_폴백_설정이_실린다(orders_spring):
    client = FakeAnthropic(
        [FakeStream([text_delta("네")], final_message([text_block("네")], "end_turn"))]
    )

    await collect(client, orders_spring)

    call = client.calls[0]
    assert call["model"] == "claude-opus-5"
    assert call["thinking"] == {"type": "adaptive"}
    assert call["fallbacks"] == "default"
    assert "server-side-fallback-2026-07-01" in call["betas"]
    assert json.dumps(call["tools"], ensure_ascii=False)  # 도구 스키마가 직렬화 가능한지
