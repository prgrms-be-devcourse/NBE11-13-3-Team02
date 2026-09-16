package com.gachisa.queue.repository

import com.gachisa.queue.dto.ExpiredAdmission
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class QueueRedisRepository(private val redisTemplate: StringRedisTemplate) {
    fun enqueue(groupBuyId: Long, userId: Long, queueToken: String) {
        redisTemplate.execute(ENQUEUE_SCRIPT, listOf(waitingKey(groupBuyId), sequenceKey(groupBuyId), tokenKey(groupBuyId), GROUPS_KEY), userId.toString(), queueToken, groupBuyId.toString())
        refreshTtl(groupBuyId)
    }
    fun admit(groupBuyId: Long, capacity: Int, batchSize: Int, expiresAt: Instant) {
        redisTemplate.execute(ADMIT_SCRIPT, listOf(waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId)), capacity.toString(), batchSize.toString(), expiresAt.toEpochMilli().toString())
        refreshTtl(groupBuyId)
    }
    fun requeueExpired(groupBuyId: Long, now: Instant): List<ExpiredAdmission> {
        val values = redisTemplate.execute(REQUEUE_EXPIRED_SCRIPT, listOf(activeKey(groupBuyId), waitingKey(groupBuyId), sequenceKey(groupBuyId), attemptKey(groupBuyId)), now.toEpochMilli().toString()) ?: emptyList<Any>()
        refreshTtl(groupBuyId)
        return values.chunked(2).mapNotNull { values ->
            if (values.size < 2) null else ExpiredAdmission(values[0].toString().toLong(), values[1].toString().takeIf { it.isNotBlank() }?.toLong())
        }
    }
    fun startConfirmation(groupBuyId: Long, userId: Long, now: Instant): Boolean {
        val result = redisTemplate.execute(START_CONFIRMATION_SCRIPT, listOf(activeKey(groupBuyId), confirmingKey(groupBuyId)), userId.toString(), now.toEpochMilli().toString())
        refreshTtl(groupBuyId); return result == 1L
    }
    fun complete(groupBuyId: Long, userId: Long) {
        redisTemplate.execute(COMPLETE_SCRIPT, allKeys(groupBuyId), userId.toString()); refreshTtl(groupBuyId)
    }
    fun requeueConfirmation(groupBuyId: Long, userId: Long) {
        redisTemplate.execute(REQUEUE_CONFIRMATION_SCRIPT, listOf(confirmingKey(groupBuyId), waitingKey(groupBuyId), sequenceKey(groupBuyId), attemptKey(groupBuyId)), userId.toString()); refreshTtl(groupBuyId)
    }
    fun bindPaymentAttempt(groupBuyId: Long, userId: Long, paymentAttemptId: Long) { redisTemplate.opsForHash<String, String>().put(attemptKey(groupBuyId), userId.toString(), paymentAttemptId.toString()); refreshTtl(groupBuyId) }
    fun getToken(groupBuyId: Long, userId: Long): String? = redisTemplate.opsForHash<String, String>().get(tokenKey(groupBuyId), userId.toString())
    fun getWaitingPosition(groupBuyId: Long, userId: Long): Long? = redisTemplate.opsForZSet().rank(waitingKey(groupBuyId), userId.toString())?.plus(1)
    fun getAdmissionExpiresAt(groupBuyId: Long, userId: Long): Instant? = redisTemplate.opsForZSet().score(activeKey(groupBuyId), userId.toString())?.toLong()?.let { Instant.ofEpochMilli(it) }
    fun isConfirming(groupBuyId: Long, userId: Long): Boolean = redisTemplate.opsForZSet().score(confirmingKey(groupBuyId), userId.toString()) != null
    fun getGroupBuyIds(): Set<String> = redisTemplate.opsForSet().members(GROUPS_KEY) ?: emptySet()
    fun deleteQueue(groupBuyId: Long) { redisTemplate.delete(allKeys(groupBuyId)); redisTemplate.opsForSet().remove(GROUPS_KEY, groupBuyId.toString()) }
    private fun refreshTtl(groupBuyId: Long) { allKeys(groupBuyId).forEach { redisTemplate.expire(it, QUEUE_IDLE_TTL) } }
    private fun allKeys(groupBuyId: Long) = listOf(waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId), tokenKey(groupBuyId), attemptKey(groupBuyId), sequenceKey(groupBuyId))
    private fun waitingKey(groupBuyId: Long) = "queue:waiting:$groupBuyId"
    private fun activeKey(groupBuyId: Long) = "queue:active:$groupBuyId"
    private fun confirmingKey(groupBuyId: Long) = "queue:confirming:$groupBuyId"
    private fun tokenKey(groupBuyId: Long) = "queue:tokens:$groupBuyId"
    private fun attemptKey(groupBuyId: Long) = "queue:attempts:$groupBuyId"
    private fun sequenceKey(groupBuyId: Long) = "queue:sequence:$groupBuyId"
    companion object {
        private const val GROUPS_KEY = "queue:groups"
        private val QUEUE_IDLE_TTL: Duration = Duration.ofHours(24)
        private val ENQUEUE_SCRIPT: DefaultRedisScript<Long> = loadScript("redis/queue-enqueue.lua", Long::class.java)
        private val ADMIT_SCRIPT: DefaultRedisScript<List<*>> = loadScript("redis/queue-admit.lua", List::class.java)
        private val REQUEUE_EXPIRED_SCRIPT: DefaultRedisScript<List<*>> = loadScript("redis/queue-requeue-expired.lua", List::class.java)
        private val START_CONFIRMATION_SCRIPT: DefaultRedisScript<Long> = loadScript("redis/queue-start-confirmation.lua", Long::class.java)
        private val COMPLETE_SCRIPT: DefaultRedisScript<Long> = loadScript("redis/queue-complete.lua", Long::class.java)
        private val REQUEUE_CONFIRMATION_SCRIPT: DefaultRedisScript<Long> = loadScript("redis/queue-requeue-confirmation.lua", Long::class.java)
        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> loadScript(path: String, resultType: Class<*>): DefaultRedisScript<T> =
            DefaultRedisScript<T>().also { script ->
                script.setLocation(ClassPathResource(path))
                script.setResultType(resultType as Class<T>)
            }
    }
}
