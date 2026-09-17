"""운영 품질 지표 수집 테스트."""

import asyncio

from app.quality import QualityMetrics


async def record(metrics: QualityMetrics, route: str, reason: str, calls: int = 2) -> None:
    await metrics.record(
        route=route, reason=reason, generation_calls=calls, prompt_labels=["p@v1+abc"]
    )


async def test_빈_상태에서는_0으로_나눈_값이_없다():
    snapshot = await QualityMetrics().snapshot()

    assert snapshot.total_messages == 0
    assert snapshot.calls_per_message == 0.0
    assert snapshot.faq_ratio == 0.0


async def test_경로와_근거를_따로_센다():
    metrics = QualityMetrics()
    await record(metrics, "faq", "embedding", calls=1)
    await record(metrics, "faq", "embedding", calls=1)
    await record(metrics, "general", "low-score")
    await record(metrics, "order", "rule:personal", calls=3)

    snapshot = await metrics.snapshot()

    assert snapshot.total_messages == 4
    assert snapshot.routes == {"faq": 2, "general": 1, "order": 1}
    assert snapshot.reasons == {"embedding": 2, "low-score": 1, "rule:personal": 1}
    assert snapshot.generation_calls == 7  # 1 + 1 + 2 + 3
    assert snapshot.calls_per_message == 1.75


async def test_faq_ratio는_호출을_아낀_비율이다():
    metrics = QualityMetrics()
    for _ in range(3):
        await record(metrics, "faq", "embedding", calls=1)
    for _ in range(7):
        await record(metrics, "general", "low-score")

    snapshot = await metrics.snapshot()

    assert snapshot.faq_ratio == 0.3


async def test_faq_miss는_general에_묻히지_않는다():
    """FAQ로 보냈다 근거가 없어 되돌아온 경우. 경로는 general로 찍히므로
    따로 세지 않으면 'FAQ 문서가 부족하다'는 신호가 드러나지 않는다."""
    metrics = QualityMetrics()
    await record(metrics, "general", "faq-miss")
    await record(metrics, "general", "low-score")

    snapshot = await metrics.snapshot()

    assert snapshot.routes == {"general": 2}
    assert snapshot.faq_miss == 1


async def test_쓰인_프롬프트를_모아둔다():
    metrics = QualityMetrics()
    await metrics.record(
        route="faq", reason="embedding", generation_calls=1, prompt_labels=["faq@v1+aaa"]
    )
    await metrics.record(
        route="general", reason="low-score", generation_calls=2, prompt_labels=["agent@v2+bbb"]
    )

    snapshot = await metrics.snapshot()

    assert snapshot.prompts == ["agent@v2+bbb", "faq@v1+aaa"]


async def test_동시에_기록해도_숫자가_새지_않는다():
    metrics = QualityMetrics()

    await asyncio.gather(*(record(metrics, "faq", "embedding", calls=1) for _ in range(200)))

    snapshot = await metrics.snapshot()
    assert snapshot.total_messages == 200
    assert snapshot.generation_calls == 200
