import json
from typing import Any

import httpx

from app.security import CurrentUser
from app.spring_client import SpringClient

TOOL_DEFINITIONS: list[dict[str, Any]] = [
    {
        "name": "search_group_buys",
        "description": (
            "진행 중인 공동구매를 검색한다. 사용자가 상품을 찾거나 둘러보거나 가격을 물을 때 사용한다. "
            "가격 조건은 할인가 기준이다. 로그인하지 않아도 조회할 수 있는 공개 정보다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "keyword": {"type": "string", "description": "상품명 검색어"},
                "min_price": {"type": "integer", "description": "최소 할인가(원)"},
                "max_price": {"type": "integer", "description": "최대 할인가(원)"},
                "status": {
                    "type": "string",
                    "enum": ["RECRUITING", "ACHIEVED", "NOT_ACHIEVED", "SETTLED", "CANCELLED"],
                    "description": "공동구매 상태. 보통 모집중인 것만 보려면 RECRUITING",
                },
                "sort": {
                    "type": "string",
                    "enum": ["price_asc", "price_desc"],
                    "description": "가격 정렬. 생략하면 마감임박순",
                },
            },
        },
    },
    {
        "name": "get_my_orders",
        "description": (
            "로그인한 사용자 본인의 주문 목록을 최신순으로 조회한다. "
            "'내 주문', '뭐 샀지', '배송 어디까지' 같은 질문에 먼저 이 도구로 주문을 찾는다. "
            "다른 사용자의 주문은 조회할 수 없다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "size": {
                    "type": "integer",
                    "description": "가져올 주문 개수(기본 10, 최대 30)",
                }
            },
        },
    },
    {
        "name": "get_order_delivery",
        "description": (
            "특정 주문의 배송 상세(운송장 번호, 택배사, 예상 도착일)를 조회한다. "
            "order_id는 get_my_orders로 먼저 확인해야 한다. 본인 주문만 조회된다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "integer", "description": "주문 ID"},
            },
            "required": ["order_id"],
        },
    },
]


def _drop_none(params: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in params.items() if v is not None}


async def _search_group_buys(
    spring: SpringClient, user: CurrentUser, args: dict[str, Any]
) -> Any:
    params = _drop_none(
        {
            "keyword": args.get("keyword"),
            "minPrice": args.get("min_price"),
            "maxPrice": args.get("max_price"),
            "status": args.get("status"),
            "sort": args.get("sort"),
            "size": 10,
        }
    )
    response = await spring.get(
        "/api/group-buys/search", access_token=user.access_token, params=params
    )
    response.raise_for_status()
    page = response.json()["result"]
    return [
        {
            "groupBuyId": g["groupBuyId"],
            "productName": g["productName"],
            "category": g["categoryName"],
            "basePrice": g["basePrice"],
            "discountedPrice": g["discountedPrice"],
            "participants": f'{g["currentCount"]}/{g["targetCount"]}',
            "deadline": g["deadline"],
            "status": g["status"],
        }
        for g in page["content"]
    ]


async def _get_my_orders(
    spring: SpringClient, user: CurrentUser, args: dict[str, Any]
) -> Any:
    size = min(int(args.get("size") or 10), 30)
    response = await spring.get(
        "/api/orders", access_token=user.access_token, params={"page": 0, "size": size}
    )
    response.raise_for_status()
    body = response.json()
    return [
        {
            "orderId": o["orderId"],
            "orderNumber": o["orderNumber"],
            "productName": o["productName"],
            "quantity": o["quantity"],
            "amount": o["amount"],
            "deliveryStatus": o["deliveryStatus"],
            "orderedAt": o["createdAt"],
        }
        for o in body["content"]
    ]


async def _get_order_delivery(
    spring: SpringClient, user: CurrentUser, args: dict[str, Any]
) -> Any:
    response = await spring.get(
        f"/api/orders/{int(args['order_id'])}/delivery", access_token=user.access_token
    )
    response.raise_for_status()
    d = response.json()
    # 수령인 연락처와 상세주소는 LLM에 보내지 않는다. 배송 상태를 답하는 데 필요 없고,
    # 외부 모델로 나가는 개인정보는 최소화한다.
    return {
        "orderNumber": d["orderNumber"],
        "productName": d["productName"],
        "deliveryStatus": d["deliveryStatus"],
        "carrier": d["carrier"],
        "trackingNumber": d["trackingNumber"],
        "shippingStartedAt": d["shippingStartedAt"],
        "expectedDeliveryAt": d["expectedDeliveryAt"],
        "deliveredAt": d["deliveredAt"],
    }


_HANDLERS = {
    "search_group_buys": _search_group_buys,
    "get_my_orders": _get_my_orders,
    "get_order_delivery": _get_order_delivery,
}


async def execute_tool(
    name: str, args: dict[str, Any], spring: SpringClient, user: CurrentUser
) -> dict[str, Any]:
    """도구를 실행하고 모델에 돌려줄 결과 dict를 만든다. 실패는 error 키로 알린다."""
    handler = _HANDLERS.get(name)
    if handler is None:
        return {"error": f"알 수 없는 도구입니다: {name}"}

    try:
        result = await handler(spring, user, args)
    except httpx.HTTPStatusError as e:
        status = e.response.status_code
        if status in (401, 403):
            return {"error": "권한이 없어 조회하지 못했습니다. 본인 정보만 조회할 수 있습니다."}
        if status == 404:
            return {"error": "해당 데이터를 찾을 수 없습니다."}
        return {"error": f"백엔드 오류({status})로 조회하지 못했습니다."}
    except httpx.RequestError:
        return {"error": "백엔드에 연결하지 못했습니다."}

    return {"result": json.loads(json.dumps(result, default=str))}
