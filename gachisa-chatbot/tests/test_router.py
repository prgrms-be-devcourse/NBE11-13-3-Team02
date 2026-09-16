"""라우터 분류 테스트.

실제 임베딩 API 대신 방향이 정해진 벡터를 쓴다. 축을 경로 하나씩에 배정하면
유사도가 곧 "질문이 어느 축을 향하는가"가 되어, 임계값 동작을 눈으로 따라갈 수 있다.

    e1 = FAQ, e2 = CATALOG, e3 = ORDER, e4 = 어느 쪽도 아님
"""

import pytest

from app.router import EXEMPLARS, MIN_MARGIN, MIN_SCORE, QuestionRouter, Route
from tests.fakes import FakeEmbedClient

FAQ_AXIS = [1.0, 0.0, 0.0, 0.0]
CATALOG_AXIS = [0.0, 1.0, 0.0, 0.0]
ORDER_AXIS = [0.0, 0.0, 1.0, 0.0]
NOISE_AXIS = [0.0, 0.0, 0.0, 1.0]

AXIS_OF = {Route.FAQ: FAQ_AXIS, Route.CATALOG: CATALOG_AXIS, Route.ORDER: ORDER_AXIS}


def build_client(query_vectors: dict[str, list[float]]) -> FakeEmbedClient:
    """예시 문장은 각자의 축으로, 질문은 인자로 받은 벡터로 임베딩되게 한다."""
    vectors = {
        text: AXIS_OF[route] for route, texts in EXEMPLARS.items() for text in texts
    }
    return FakeEmbedClient({**vectors, **query_vectors})


async def make_router(query_vectors: dict[str, list[float]]) -> tuple[QuestionRouter, FakeEmbedClient]:
    client = build_client(query_vectors)
    return await QuestionRouter.build(client), client


async def test_예시_임베딩은_기동_시_한_번만_만든다():
    router, client = await make_router({})
    embedded_at_build = len(client.embedded)

    await router.classify("아무 질문")

    total_exemplars = sum(len(texts) for texts in EXEMPLARS.values())
    assert embedded_at_build == total_exemplars
    # 분류는 질문 하나만 추가로 임베딩한다.
    assert len(client.embedded) == total_exemplars + 1


async def test_정책_질문은_FAQ로_간다():
    router, _ = await make_router({"환불은 어떻게 하나요": FAQ_AXIS})

    decision = await router.classify("환불은 어떻게 하나요")

    assert decision.route is Route.FAQ
    assert decision.reason == "embedding"
    assert decision.tools == ()


async def test_상품_질문은_CATALOG로_가고_도구가_좁혀진다():
    router, _ = await make_router({"만원 이하 뭐 있어": CATALOG_AXIS})

    decision = await router.classify("만원 이하 뭐 있어")

    assert decision.route is Route.CATALOG
    assert set(decision.tools) == {"search_group_buys", "search_faq"}
    # 카탈로그 경로에 개인 주문 도구가 열려 있으면 좁힌 의미가 없다.
    assert "get_my_orders" not in decision.tools


@pytest.mark.parametrize(
    "message",
    ["내 주문 어디까지 왔어", "운송장 번호 알려줘", "내가 산 거 보여줘", "제 주문 취소해줘"],
)
async def test_본인_데이터를_가리키면_임베딩_없이_ORDER로_간다(message):
    router, client = await make_router({})
    before = len(client.embedded)

    decision = await router.classify(message)

    assert decision.route is Route.ORDER
    assert decision.reason == "rule:personal"
    # 규칙으로 끝났으므로 임베딩 호출이 없어야 한다.
    assert len(client.embedded) == before


async def test_어느_경로와도_닮지_않으면_GENERAL로_떨어진다():
    # 대부분 noise 축을 향해 1등 유사도가 임계값에 못 미치는 질문.
    router, _ = await make_router({"오늘 날씨 어때": [0.4, 0.0, 0.0, 0.9]})

    decision = await router.classify("오늘 날씨 어때")

    assert decision.route is Route.GENERAL
    assert decision.reason == "low-score"
    assert decision.score < MIN_SCORE
    # GENERAL은 도구를 전부 연다.
    assert decision.tools is None


async def test_두_경로에_비슷하게_걸리면_단정하지_않는다():
    # 점수는 높은데 1등과 2등을 가를 수 없는 경우. 축을 직교로 두면 두 경로에
    # 동시에 높은 점수가 나올 수 없으므로, 예시끼리 가까이 붙은 상황을 직접 만든다.
    # 질문은 두 예시 사이 정확히 가운데에 놓여 양쪽과 0.98로 같다.
    client = FakeEmbedClient({"공동구매 싸게 사는 법": [1.0, 0.0, 0.0, 0.0]})
    router = QuestionRouter(
        [
            (Route.FAQ, [0.98058, 0.19612, 0.0, 0.0]),
            (Route.CATALOG, [0.98058, -0.19612, 0.0, 0.0]),
            (Route.ORDER, ORDER_AXIS),
        ],
        client,
    )

    decision = await router.classify("공동구매 싸게 사는 법")

    # 점수 자체는 임계값을 넘는다. 걸러낸 이유가 margin 이어야 한다.
    assert decision.score >= MIN_SCORE
    assert decision.route is Route.GENERAL
    assert decision.reason == "ambiguous"


async def test_margin을_살짝_넘으면_경로를_고른다():
    # FAQ 쪽으로 충분히 기울어 2등과의 차이가 MIN_MARGIN을 넘는 경우.
    router, _ = await make_router({"환불 규정": [1.0, 0.7, 0.0, 0.0]})

    decision = await router.classify("환불 규정")

    assert decision.route is Route.FAQ
    assert decision.score - 0.7 / (1.0**2 + 0.7**2) ** 0.5 > MIN_MARGIN


async def test_이미지가_있으면_경로를_좁히지_않는다():
    router, client = await make_router({"이거 뭐야": FAQ_AXIS})
    before = len(client.embedded)

    decision = await router.classify("이거 뭐야", has_image=True)

    assert decision.route is Route.GENERAL
    assert decision.reason == "image"
    assert len(client.embedded) == before


async def test_임베딩이_실패해도_대화가_죽지_않는다():
    router, client = await make_router({})
    client.fail = True

    decision = await router.classify("환불 규정이 뭐야")

    assert decision.route is Route.GENERAL
    assert decision.reason == "embed-failed"
