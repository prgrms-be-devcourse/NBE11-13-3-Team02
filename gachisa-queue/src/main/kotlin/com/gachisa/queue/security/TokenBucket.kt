package com.gachisa.queue.security

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 토큰 버킷 알고리즘. 버킷에 [capacity]개의 토큰이 있고, 초당 [refillPerSecond]개씩 채워진다.
 * 요청마다 토큰을 하나 꺼내 쓰고, 없으면 거절한다.
 *
 * 고정 윈도(예: "분당 N회")와 달리 순간적인 burst를 허용하면서도 평균 속도는 억제한다.
 * 백그라운드 타이머로 채우지 않고, 확인 시점마다 경과 시간만큼 채운 것으로 계산한다
 * (lazy refill) — 유휴 상태인 버킷 수만큼 스레드/코루틴을 띄울 필요가 없다.
 */
class TokenBucket(
    private val capacity: Double,
    private val refillPerSecond: Double,
    private val clock: () -> Instant = Instant::now,
) {
    private var tokens = capacity
    private var lastRefillAt = clock()
    private val mutex = Mutex()

    suspend fun tryConsume(cost: Double = 1.0): Boolean = mutex.withLock {
        refill()
        if (tokens >= cost) {
            tokens -= cost
            true
        } else {
            false
        }
    }

    private fun refill() {
        val now = clock()
        val elapsedSeconds = Duration.between(lastRefillAt, now).toMillis() / 1000.0
        if (elapsedSeconds <= 0) return
        tokens = (tokens + elapsedSeconds * refillPerSecond).coerceAtMost(capacity)
        lastRefillAt = now
    }
}
