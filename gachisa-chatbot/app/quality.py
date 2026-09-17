"""운영 중 품질 지표를 모은다.

평가셋은 "고치기 전에 재는" 도구다. 배포하고 나면 실제 질문이 평가셋과 다르게
생겼는지, 라우팅이 기대대로 도는지는 평가셋이 알려주지 않는다. 그래서 요청마다
남는 것을 세어 둔다.

무엇을 세는가는 "무엇이 나빠지면 알아야 하는가"에서 나왔다.

    경로 분포        faq 비중이 갑자기 떨어지면 라우팅이 죽은 것이다
    판단 근거        low-score 가 늘면 예시가 실제 질문을 못 따라가는 것이다
    faq-miss         라우터는 FAQ 라 했는데 근거가 없던 비율. 높으면 FAQ 문서가 부족하다
    질문당 생성 호출  라우팅이 실제로 호출을 아끼고 있는지. 무료 한도와 직결된다
    프롬프트 digest   이 숫자들이 어느 프롬프트에서 나왔는지

집계는 프로세스 메모리에 있다. 재시작하면 0부터 다시 쌓인다. 사용량 집계
(ChatUsageLimiter)와 같은 한계이며, 장기 보관이 필요해지면 둘 다 저장소를 붙여야
한다. 지금 단계에서 저장소를 붙이면 운영 요소만 늘고 얻는 게 없다.
"""

import asyncio
from collections import Counter
from dataclasses import dataclass, field


@dataclass(frozen=True)
class QualitySnapshot:
    total_messages: int
    # 경로 이름 → 횟수. 예: {"faq": 12, "general": 30}
    routes: dict[str, int]
    # 판단 근거 → 횟수. 예: {"embedding": 25, "low-score": 10, "rule:personal": 7}
    reasons: dict[str, int]
    faq_miss: int
    generation_calls: int
    # 이 기간에 쓰인 프롬프트들. 배포 중 바뀌면 둘 이상이 찍힌다.
    prompts: list[str] = field(default_factory=list)

    @property
    def calls_per_message(self) -> float:
        return self.generation_calls / self.total_messages if self.total_messages else 0.0

    @property
    def faq_ratio(self) -> float:
        """호출을 아낀 비율. 라우팅이 값을 내고 있는지 한 숫자로 보는 지표다."""
        return self.routes.get("faq", 0) / self.total_messages if self.total_messages else 0.0


class QualityMetrics:
    """요청마다 한 번 record 한다. 락은 카운터 갱신 구간만 잡는다."""

    def __init__(self) -> None:
        self._routes: Counter[str] = Counter()
        self._reasons: Counter[str] = Counter()
        self._prompts: Counter[str] = Counter()
        self._faq_miss = 0
        self._generation_calls = 0
        self._total = 0
        self._lock = asyncio.Lock()

    async def record(
        self,
        *,
        route: str,
        reason: str,
        generation_calls: int,
        prompt_labels: list[str],
    ) -> None:
        async with self._lock:
            self._total += 1
            self._routes[route] += 1
            self._reasons[reason] += 1
            self._generation_calls += generation_calls
            # faq-miss 는 라우터가 FAQ 로 보냈는데 근거가 없어 되돌아온 경우다.
            # 경로는 general 로 찍히므로 따로 세지 않으면 드러나지 않는다.
            if reason == "faq-miss":
                self._faq_miss += 1
            for label in prompt_labels:
                self._prompts[label] += 1

    async def snapshot(self) -> QualitySnapshot:
        async with self._lock:
            return QualitySnapshot(
                total_messages=self._total,
                routes=dict(self._routes),
                reasons=dict(self._reasons),
                faq_miss=self._faq_miss,
                generation_calls=self._generation_calls,
                prompts=sorted(self._prompts),
            )
