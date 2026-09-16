import asyncio
import time
from datetime import date, datetime, timedelta, timezone
from typing import Callable

KST = timezone(timedelta(hours=9))


class RateLimitExceeded(Exception):
    """짧은 시간에 너무 많이 요청했다. 잠시 후 재시도하면 된다."""

    def __init__(self, retry_after_seconds: float) -> None:
        self.retry_after_seconds = retry_after_seconds
        super().__init__(f"{retry_after_seconds:.0f}초 후 다시 시도해 주세요.")


class DailyBudgetExceeded(Exception):
    """오늘 쓸 수 있는 대화 횟수를 다 썼다. 재시도로는 해결되지 않는다."""


class TokenBucket:
    """토큰 버킷. 순간적인 burst는 허용하되 평균 속도는 억제한다.

    백그라운드 타이머로 채우지 않고, 확인 시점마다 경과 시간만큼 채운 것으로
    계산한다(lazy refill) — 사용자 수만큼 타이머를 띄울 필요가 없다.
    """

    def __init__(
        self,
        capacity: float,
        refill_per_second: float,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self._capacity = capacity
        self._refill_per_second = refill_per_second
        self._clock = clock
        self._tokens = capacity
        self._updated_at = clock()
        self._lock = asyncio.Lock()

    async def try_consume(self, cost: float = 1.0) -> bool:
        async with self._lock:
            self._refill()
            if self._tokens >= cost:
                self._tokens -= cost
                return True
            return False

    def _refill(self) -> None:
        now = self._clock()
        elapsed = now - self._updated_at
        if elapsed <= 0:
            return
        self._tokens = min(self._capacity, self._tokens + elapsed * self._refill_per_second)
        self._updated_at = now


class ChatUsageLimiter:
    """사용자별 AI 사용량을 두 겹으로 제한한다.

    Gemini 무료 티어는 모델당 분당 5회, 하루 20회를 프로젝트 전체가 나눠 쓴다
    (gachisa-chatbot/README.md 참고). 도구를 쓰는 대화 하나가 API를 2~3회 쓰므로,
    한 사용자가 짧은 시간에 몰아 쓰면 다른 사용자는 그 순간 아무것도 못 하게 된다.

    - 토큰 버킷(burst_capacity/refill_per_minute): 분당 공유 한도를 한 사용자가
      독점하지 못하게 짧은 시간의 속도를 억제한다.
    - 일일 한도(daily_message_limit): 하루 공유 한도를 사용자 수만큼 미리 나눠
      가진다. 재시도로 풀리지 않는 하드 캡이라 토큰 버킷과는 성격이 다르다.

    사용자별 상태를 프로세스 메모리에 둔다. 인스턴스 하나로 운영하는 지금
    구성에서는 문제없지만, 여러 인스턴스로 늘리면 사용자가 어느 인스턴스로
    가느냐에 따라 한도가 갈라진다 — 그때는 Redis 같은 공유 저장소가 필요하다.
    """

    def __init__(
        self,
        burst_capacity: float,
        refill_per_minute: float,
        daily_message_limit: int,
        clock: Callable[[], float] = time.monotonic,
        today: Callable[[], date] = lambda: datetime.now(KST).date(),
    ) -> None:
        self._burst_capacity = burst_capacity
        self._refill_per_second = refill_per_minute / 60.0
        self._daily_limit = daily_message_limit
        self._clock = clock
        self._today = today
        self._buckets: dict[int, TokenBucket] = {}
        self._daily_counts: dict[int, tuple[date, int]] = {}
        self._lock = asyncio.Lock()

    async def check(self, user_id: int) -> None:
        """이번 대화를 진행해도 되는지 확인한다. 안 되면 예외를 던진다."""
        bucket = await self._bucket_for(user_id)
        if not await bucket.try_consume():
            retry_after = (
                1.0 / self._refill_per_second if self._refill_per_second > 0 else 60.0
            )
            raise RateLimitExceeded(retry_after)

        await self._consume_daily_budget(user_id)

    async def _bucket_for(self, user_id: int) -> TokenBucket:
        async with self._lock:
            bucket = self._buckets.get(user_id)
            if bucket is None:
                bucket = TokenBucket(
                    self._burst_capacity, self._refill_per_second, clock=self._clock
                )
                self._buckets[user_id] = bucket
            return bucket

    async def _consume_daily_budget(self, user_id: int) -> None:
        async with self._lock:
            today = self._today()
            recorded_date, count = self._daily_counts.get(user_id, (today, 0))
            if recorded_date != today:
                count = 0
            if count >= self._daily_limit:
                raise DailyBudgetExceeded()
            self._daily_counts[user_id] = (today, count + 1)
