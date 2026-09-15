package com.gachisa.queue.redis

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository

/** 대기열에서 입장이 만료된 사용자와, 있다면 물려 있던 결제 시도 번호. */
data class ExpiredAdmission(val userId: Long, val paymentAttemptId: Long?)

/**
 * 대기열 상태 저장소. 모든 상태 전이는 Lua 스크립트 한 번으로 원자적으로 처리한다.
 *
 * Lettuce는 논블로킹 드라이버라 리액티브 템플릿을 쓰면 요청 처리 중 스레드를 점유하지 않는다.
 * 이 서비스가 코루틴으로 이득을 보는 이유가 여기에 있다 — JDBC처럼 붙잡는 구간이 없다.
 */
@Repository
class QueueRedisRepository(private val redis: ReactiveStringRedisTemplate) {

    private companion object {
        const val GROUPS_KEY = "queue:groups"
        val IDLE_TTL: Duration = Duration.ofHours(24)

        val ENQUEUE = longScript("redis/queue-enqueue.lua")
        val ADMIT = listScript("redis/queue-admit.lua")
        val REQUEUE_EXPIRED = listScript("redis/queue-requeue-expired.lua")
        val START_CONFIRMATION = longScript("redis/queue-start-confirmation.lua")
        val COMPLETE = longScript("redis/queue-complete.lua")
        val REQUEUE_CONFIRMATION = longScript("redis/queue-requeue-confirmation.lua")

        fun longScript(path: String): RedisScript<Long> =
            RedisScript.of(ClassPathResource(path), Long::class.javaObjectType)

        @Suppress("UNCHECKED_CAST")
        fun listScript(path: String): RedisScript<List<Any?>> =
            RedisScript.of(ClassPathResource(path), List::class.java) as RedisScript<List<Any?>>
    }

    private fun waitingKey(groupBuyId: Long) = "queue:waiting:$groupBuyId"
    private fun activeKey(groupBuyId: Long) = "queue:active:$groupBuyId"
    private fun confirmingKey(groupBuyId: Long) = "queue:confirming:$groupBuyId"
    private fun tokenKey(groupBuyId: Long) = "queue:tokens:$groupBuyId"
    private fun attemptKey(groupBuyId: Long) = "queue:attempts:$groupBuyId"
    private fun sequenceKey(groupBuyId: Long) = "queue:sequence:$groupBuyId"

    suspend fun enqueue(groupBuyId: Long, userId: Long, queueToken: String) {
        redis.execute(
            ENQUEUE,
            listOf(waitingKey(groupBuyId), sequenceKey(groupBuyId), tokenKey(groupBuyId), GROUPS_KEY),
            listOf(userId.toString(), queueToken, groupBuyId.toString()),
        ).awaitFirstOrNull()
        refreshTtl(groupBuyId)
    }

    suspend fun admit(groupBuyId: Long, capacity: Int, batchSize: Int, expiresAt: Instant) {
        redis.execute(
            ADMIT,
            listOf(waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId)),
            listOf(capacity.toString(), batchSize.toString(), expiresAt.toEpochMilli().toString()),
        ).awaitFirstOrNull()
        refreshTtl(groupBuyId)
    }

    suspend fun requeueExpired(groupBuyId: Long, now: Instant): List<ExpiredAdmission> {
        val values = redis.execute(
            REQUEUE_EXPIRED,
            listOf(
                activeKey(groupBuyId), waitingKey(groupBuyId),
                sequenceKey(groupBuyId), attemptKey(groupBuyId),
            ),
            listOf(now.toEpochMilli().toString()),
        ).awaitFirstOrNull().orEmpty()
        refreshTtl(groupBuyId)

        // 스크립트는 [userId, attemptId, userId, attemptId, ...] 형태로 평평하게 돌려준다.
        return values.chunked(2).mapNotNull { pair ->
            val userId = pair.getOrNull(0)?.toString()?.toLongOrNull() ?: return@mapNotNull null
            val attemptId = pair.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() }?.toLongOrNull()
            ExpiredAdmission(userId, attemptId)
        }
    }

    suspend fun startConfirmation(groupBuyId: Long, userId: Long, now: Instant): Boolean {
        val result = redis.execute(
            START_CONFIRMATION,
            listOf(activeKey(groupBuyId), confirmingKey(groupBuyId)),
            listOf(userId.toString(), now.toEpochMilli().toString()),
        ).awaitFirstOrNull()
        refreshTtl(groupBuyId)
        return result == 1L
    }

    suspend fun complete(groupBuyId: Long, userId: Long) {
        redis.execute(
            COMPLETE,
            listOf(
                waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId),
                tokenKey(groupBuyId), attemptKey(groupBuyId),
            ),
            listOf(userId.toString()),
        ).awaitFirstOrNull()
        refreshTtl(groupBuyId)
    }

    suspend fun requeueConfirmation(groupBuyId: Long, userId: Long) {
        redis.execute(
            REQUEUE_CONFIRMATION,
            listOf(
                confirmingKey(groupBuyId), waitingKey(groupBuyId),
                sequenceKey(groupBuyId), attemptKey(groupBuyId),
            ),
            listOf(userId.toString()),
        ).awaitFirstOrNull()
        refreshTtl(groupBuyId)
    }

    suspend fun bindPaymentAttempt(groupBuyId: Long, userId: Long, paymentAttemptId: Long) {
        redis.opsForHash<String, String>()
            .put(attemptKey(groupBuyId), userId.toString(), paymentAttemptId.toString())
            .awaitSingle()
        refreshTtl(groupBuyId)
    }

    suspend fun getToken(groupBuyId: Long, userId: Long): String? =
        redis.opsForHash<String, String>()
            .get(tokenKey(groupBuyId), userId.toString())
            .awaitSingleOrNull()

    /** 대기 순번(1부터). 대기열에 없으면 null. */
    suspend fun getWaitingPosition(groupBuyId: Long, userId: Long): Long? =
        redis.opsForZSet()
            .rank(waitingKey(groupBuyId), userId.toString())
            .awaitSingleOrNull()
            ?.plus(1)

    suspend fun getAdmissionExpiresAt(groupBuyId: Long, userId: Long): Instant? =
        redis.opsForZSet()
            .score(activeKey(groupBuyId), userId.toString())
            .awaitSingleOrNull()
            ?.let { Instant.ofEpochMilli(it.toLong()) }

    suspend fun isConfirming(groupBuyId: Long, userId: Long): Boolean =
        redis.opsForZSet()
            .score(confirmingKey(groupBuyId), userId.toString())
            .awaitSingleOrNull() != null

    suspend fun getGroupBuyIds(): List<Long> =
        redis.opsForSet().members(GROUPS_KEY)
            .collectList().awaitSingle()
            .mapNotNull { it.toLongOrNull() }

    suspend fun deleteQueue(groupBuyId: Long) {
        redis.delete(*keysOf(groupBuyId).toTypedArray()).awaitSingleOrNull()
        redis.opsForSet().remove(GROUPS_KEY, groupBuyId.toString()).awaitSingleOrNull()
    }

    private suspend fun refreshTtl(groupBuyId: Long) {
        keysOf(groupBuyId).forEach { redis.expire(it, IDLE_TTL).awaitSingleOrNull() }
    }

    private fun keysOf(groupBuyId: Long) = listOf(
        waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId),
        tokenKey(groupBuyId), attemptKey(groupBuyId), sequenceKey(groupBuyId),
    )
}
