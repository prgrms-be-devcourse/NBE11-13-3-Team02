from datetime import date

import pytest

from app.rate_limit import ChatUsageLimiter, DailyBudgetExceeded, RateLimitExceeded, TokenBucket


class FakeClock:
    """수동으로 시간을 흘려보낼 수 있는 시계. 실제로 sleep하지 않고도 보충을 검증한다."""

    def __init__(self, start: float = 0.0) -> None:
        self.now = start

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


async def test_용량만큼은_즉시_소비할_수_있다():
    clock = FakeClock()
    bucket = TokenBucket(capacity=3, refill_per_second=0, clock=clock)

    assert await bucket.try_consume()
    assert await bucket.try_consume()
    assert await bucket.try_consume()
    assert not await bucket.try_consume()


async def test_시간이_지나면_보충된다():
    clock = FakeClock()
    bucket = TokenBucket(capacity=2, refill_per_second=1, clock=clock)

    assert await bucket.try_consume()
    assert await bucket.try_consume()
    assert not await bucket.try_consume()

    clock.advance(1)
    assert await bucket.try_consume()
    assert not await bucket.try_consume()


async def test_보충량은_용량을_넘지_않는다():
    clock = FakeClock()
    bucket = TokenBucket(capacity=2, refill_per_second=1, clock=clock)

    clock.advance(3600)  # 오래 쉬어도 용량 이상 쌓이면 안 된다

    assert await bucket.try_consume()
    assert await bucket.try_consume()
    assert not await bucket.try_consume()


@pytest.fixture
def limiter():
    clock = FakeClock()
    today = FakeClock(start=0)
    day = date(2026, 9, 16)
    return ChatUsageLimiter(
        burst_capacity=2,
        refill_per_minute=1,
        daily_message_limit=3,
        clock=clock,
        today=lambda: day,
    ), clock


async def test_버스트_용량을_넘으면_재시도_예외를_던진다(limiter):
    usage, _clock = limiter

    await usage.check(user_id=1)
    await usage.check(user_id=1)
    with pytest.raises(RateLimitExceeded):
        await usage.check(user_id=1)


async def test_일일_한도를_넘으면_일일_한도_예외를_던진다():
    clock = FakeClock()
    day = date(2026, 9, 16)
    # 버스트 용량을 넉넉히 둬서 일일 한도만 걸리게 한다.
    usage = ChatUsageLimiter(
        burst_capacity=10, refill_per_minute=0, daily_message_limit=2,
        clock=clock, today=lambda: day,
    )

    await usage.check(user_id=1)
    await usage.check(user_id=1)
    with pytest.raises(DailyBudgetExceeded):
        await usage.check(user_id=1)


async def test_사용자마다_독립적으로_집계한다():
    clock = FakeClock()
    day = date(2026, 9, 16)
    usage = ChatUsageLimiter(
        burst_capacity=1, refill_per_minute=0, daily_message_limit=1,
        clock=clock, today=lambda: day,
    )

    await usage.check(user_id=1)
    with pytest.raises(RateLimitExceeded):
        await usage.check(user_id=1)

    # 사용자 1이 막혔어도 사용자 2는 영향받지 않는다.
    await usage.check(user_id=2)


async def test_날짜가_바뀌면_일일_한도가_초기화된다():
    clock = FakeClock()
    day = date(2026, 9, 16)
    current_day = {"value": day}
    usage = ChatUsageLimiter(
        burst_capacity=10, refill_per_minute=0, daily_message_limit=1,
        clock=clock, today=lambda: current_day["value"],
    )

    await usage.check(user_id=1)
    with pytest.raises(DailyBudgetExceeded):
        await usage.check(user_id=1)

    current_day["value"] = date(2026, 9, 17)
    await usage.check(user_id=1)  # 다음 날이라 다시 허용된다
