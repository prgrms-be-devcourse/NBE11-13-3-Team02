"""질문을 경로로 나누는 라우터.

에이전트에게 매번 "어떤 도구를 쓸지"를 묻는 대신, 질문의 성격을 먼저 판단해
경로를 고정한다. 정책 질문처럼 FAQ 하나로 끝나는 질문은 도구 호출 왕복을
통째로 건너뛰고 생성 모델을 한 번만 부른다.

무료 티어는 모델당 분당 5회다. 도구를 쓰는 질문은 "도구를 고르는 호출"과
"답변을 만드는 호출"로 최소 2회를 쓰므로 연달아 물으면 곧장 429가 난다.
라우팅이 줄이는 것은 이 생성 모델 호출이다. 분류에 쓰는 임베딩은 모델이
달라(gemini-embedding-001) 쿼터를 따로 쓰므로 병목을 건드리지 않는다.

분류를 틀렸을 때의 비용이 경로마다 다르다는 점이 설계의 중심이다.
FAQ로 잘못 보내면 도구를 못 써서 엉뚱한 답이 나가지만, GENERAL로 잘못
보내면 기존 동작 그대로라 손해가 호출 한 번뿐이다. 그래서 확신이 서지
않으면 언제나 GENERAL로 떨어뜨린다.
"""

import logging
import re
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from app.rag import FAQ_DIR, cosine, embed_queries, load_chunks

logger = logging.getLogger(__name__)


class Route(StrEnum):
    FAQ = "faq"
    CATALOG = "catalog"
    ORDER = "order"
    GENERAL = "general"


# 경로별로 열어줄 도구. 좁힐수록 모델이 엉뚱한 도구를 고를 여지가 줄고,
# 도구 정의가 매 호출 프롬프트에 실리므로 입력 토큰도 함께 줄어든다.
# FAQ는 빈 튜플이다. 검색 결과를 미리 넣어주고 도구 없이 한 번에 답하게 한다.
# GENERAL은 판단이 서지 않은 경우이므로 전부 연다(None = 전체).
ROUTE_TOOLS: dict[Route, tuple[str, ...] | None] = {
    Route.FAQ: (),
    Route.CATALOG: ("search_group_buys", "search_faq"),
    Route.ORDER: ("get_my_orders", "get_order_delivery", "search_faq"),
    Route.GENERAL: None,
}

# 각 경로를 대표하는 질문들. 임베딩해 두고 들어온 질문과 유사도를 비교한다.
# 중심점(평균)이 아니라 개별 문장과의 최댓값을 쓴다. 한 경로 안에서도 질문
# 형태가 제각각이라 평균을 내면 어느 쪽과도 닮지 않은 벡터가 나온다.
#
# FAQ 경로의 예시는 여기 없다. FAQ 문서의 소제목을 그대로 쓴다(faq_exemplars).
# 카탈로그와 주문은 대응하는 문서가 없어 손으로 적는다.
EXEMPLARS: dict[Route, tuple[str, ...]] = {
    Route.CATALOG: (
        "5만원 이하 공동구매 뭐 있어",
        "지금 모집 중인 상품 보여줘",
        "원두 공동구매 있어?",
        "제일 싼 공동구매 추천해줘",
        "마감 임박한 공동구매 알려줘",
        "텀블러 같은 거 팔아?",
    ),
    Route.ORDER: (
        "내 주문 어디까지 왔어",
        "배송 언제 도착해",
        "내가 뭐 샀는지 알려줘",
        "주문 취소하고 싶어",
        "운송장 번호 알려줘",
        "결제한 내역 보여줘",
    ),
}

# 본인 데이터를 가리키는 표현. 이게 걸리면 임베딩을 볼 것도 없이 ORDER다.
# FAQ 문서에도 "배송", "환불" 같은 낱말이 나오므로, 낱말 하나가 아니라
# 소유·지시 표현과 함께 나올 때만 잡는다.
_PERSONAL_PATTERN = re.compile(
    r"(내\s*주문|제\s*주문|나의\s*주문|내가\s*(산|주문|결제)|주문\s*번호"
    r"|운송장|송장|어디까지\s*왔|언제\s*(도착|와)|배송\s*조회|내\s*결제)"
)

# 1등 경로와의 유사도 하한. 이보다 낮으면 어느 경로와도 닮지 않은 질문이다.
# 1등과 2등의 차이. 붙어 있으면 둘 중 하나로 단정하지 않는다.
#
# 두 값은 evals/routing.yaml 50문항을 훑어 정했다(scripts/check_router.py --sweep).
#
#   min_score  margin   맞음  안전  틀림
#        0.55    0.02     43     2     5
#        0.60    0.02     43     2     5
#        0.60    0.05     43     6     1   ← 선택
#        0.65    0.05     42     7     1
#        0.75    0.05     37    12     1
#        0.80    0.05     32    17     1
#
# margin 0.05 를 지키는 한 min_score 를 0.55 까지 내려도 잘못된 경로는 1건에서
# 늘지 않는다. 그 1건은 임계값과 무관한 규칙 판정(adv-05)이다. 반대로 margin 을
# 0.02 로 좁히면 곧바로 5건으로 뛴다. 오분류를 막는 것은 점수 하한이 아니라
# 1등과 2등을 가르는 margin 이다.
#
# 그래서 margin 은 0.05 로 두고 min_score 는 이득이 평평해지는 0.60 에 둔다.
# 더 내려도 적중이 오르지 않으므로 여유를 남긴다.
#
# 예시를 FAQ 문서에서 뽑기 전에는 0.75 가 최선이었다(37/12/1). 예시가 주제를
# 다 덮지 못해 FAQ 질문이 0.58~0.68 에 몰렸고, 그 아래로 내리면 관계없는 질문까지
# 딸려 들어왔다. 예시를 고치자 임계값의 최적점도 함께 움직였다.
MIN_SCORE = 0.60
MIN_MARGIN = 0.05


def faq_exemplars(directory=FAQ_DIR) -> tuple[str, ...]:
    """FAQ 문서의 소제목을 FAQ 경로의 예시로 쓴다.

    소제목 하나가 곧 FAQ가 답할 수 있는 질문 하나다("어떤 방법으로 로그인하나요").
    그래서 "이 질문이 FAQ로 답이 되는가"를 재는 데 이보다 나은 목록이 없다.

    처음에는 예시를 손으로 적었는데, 공동구매·환불 쪽에 쏠려 계정·대기열 주제가
    통째로 빠졌다. 그 주제의 질문들이 유사도 0.58~0.68로 임계값에 못 미쳐 전부
    일반 경로로 떨어졌다. 문서에서 뽑으면 문서가 늘어도 예시가 따라간다.
    """
    return tuple(chunk.title.split(" > ", 1)[-1] for chunk in load_chunks(directory))


@dataclass(frozen=True)
class RouteDecision:
    route: Route
    # 왜 이 경로가 됐는지. 로그와 SSE 이벤트로 나가 디버깅과 시연에 쓰인다.
    reason: str
    score: float = 0.0
    # 분류하며 뽑은 질문 벡터. FAQ 경로가 검색에 재사용해 임베딩 호출을 아낀다.
    # 규칙으로 결정했거나 임베딩이 실패하면 None이다.
    vector: list[float] | None = None

    @property
    def tools(self) -> tuple[str, ...] | None:
        return ROUTE_TOOLS[self.route]


class QuestionRouter:
    """질문을 경로로 분류한다. 예시 문장 임베딩은 기동 시 한 번만 만든다."""

    def __init__(
        self,
        exemplar_vectors: list[tuple[Route, list[float]]],
        client: Any,
        *,
        min_score: float = MIN_SCORE,
        min_margin: float = MIN_MARGIN,
    ) -> None:
        self._exemplars = exemplar_vectors
        self._client = client
        self._min_score = min_score
        self._min_margin = min_margin

    @classmethod
    async def build(cls, client: Any, **kwargs: Any) -> "QuestionRouter":
        pairs = [(Route.FAQ, text) for text in faq_exemplars()]
        pairs += [(route, text) for route, texts in EXEMPLARS.items() for text in texts]
        vectors = await embed_queries(client, [text for _, text in pairs])
        logger.info("라우터 예시 임베딩 완료: %d개", len(vectors))
        return cls(
            [(route, vector) for (route, _), vector in zip(pairs, vectors, strict=True)],
            client,
            **kwargs,
        )

    async def classify(self, message: str, *, has_image: bool = False) -> RouteDecision:
        # 이미지는 시각 이해와 검색을 엮어야 해 경로를 좁히면 오히려 답이 나빠진다.
        if has_image:
            return RouteDecision(Route.GENERAL, "image")

        rule = self.rule_route(message)
        if rule is not None:
            return rule

        try:
            [vector] = await embed_queries(self._client, [message])
        except Exception:
            # 분류는 부가 기능이다. 실패하면 기존 동작으로 돌아가면 그만이라
            # 대화 전체를 죽이지 않는다.
            logger.warning("라우팅 임베딩 실패, GENERAL로 진행", exc_info=True)
            return RouteDecision(Route.GENERAL, "embed-failed")

        return self.decide(vector)

    @property
    def exemplars(self) -> list[tuple[Route, list[float]]]:
        """임계값만 바꾼 라우터를 다시 만들 때 재사용한다(임베딩을 다시 뽑지 않도록)."""
        return self._exemplars

    def rule_route(self, message: str) -> RouteDecision | None:
        """임베딩 없이 판단되는 경우만 처리한다. 아니면 None."""
        if _PERSONAL_PATTERN.search(message):
            return RouteDecision(Route.ORDER, "rule:personal")
        return None

    def scores(self, vector: list[float]) -> dict[Route, float]:
        """경로별 최고 유사도. 임계값을 재는 도구가 이 값을 그대로 쓴다."""
        best: dict[Route, float] = {}
        for route, exemplar in self._exemplars:
            score = cosine(vector, exemplar)
            if score > best.get(route, -1.0):
                best[route] = score
        return best

    def decide(self, vector: list[float]) -> RouteDecision:
        """질문 벡터 하나로 경로를 정한다. I/O가 없어 임계값 실험에 그대로 쓴다."""
        ranked = sorted(self.scores(vector).items(), key=lambda pair: pair[1], reverse=True)
        (top_route, top_score), (_, second_score) = ranked[0], ranked[1]

        if top_score < self._min_score:
            return RouteDecision(Route.GENERAL, "low-score", round(top_score, 3), vector)
        if top_score - second_score < self._min_margin:
            return RouteDecision(Route.GENERAL, "ambiguous", round(top_score, 3), vector)
        return RouteDecision(top_route, "embedding", round(top_score, 3), vector)
