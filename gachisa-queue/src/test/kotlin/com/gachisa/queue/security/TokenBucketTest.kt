package com.gachisa.queue.security

import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TokenBucketTest {

    @Test
    fun `용량만큼은 즉시 소비할 수 있다`() = runTest {
        val bucket = TokenBucket(capacity = 3.0, refillPerSecond = 0.0)

        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume(), "네 번째 요청은 거절돼야 한다")
    }

    @Test
    fun `시간이 지나면 보충 속도만큼 다시 채워진다`() = runTest {
        var now = Instant.parse("2026-09-16T00:00:00Z")
        val bucket = TokenBucket(capacity = 2.0, refillPerSecond = 1.0, clock = { now })

        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume(), "고갈된 직후에는 거절돼야 한다")

        now = now.plus(Duration.ofSeconds(1))
        assertTrue(bucket.tryConsume(), "1초 뒤 토큰 1개가 채워져야 한다")
        assertFalse(bucket.tryConsume(), "채워진 토큰은 하나뿐이라 두 번째는 거절돼야 한다")
    }

    @Test
    fun `보충량은 용량을 넘지 않는다`() = runTest {
        var now = Instant.parse("2026-09-16T00:00:00Z")
        val bucket = TokenBucket(capacity = 2.0, refillPerSecond = 1.0, clock = { now })

        now = now.plus(Duration.ofHours(1)) // 오래 쉬어도 용량 이상 쌓이면 안 된다

        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume())
    }

    @Test
    fun `동시에 몰려도 소비한 총량이 보유량을 넘지 않는다`() = runTest {
        val bucket = TokenBucket(capacity = 10.0, refillPerSecond = 0.0)

        val results = coroutineScope {
            (1..30).map { async { bucket.tryConsume() } }.awaitAll()
        }

        assertEquals(10, results.count { it }, "정확히 용량만큼만 성공해야 한다")
    }
}
