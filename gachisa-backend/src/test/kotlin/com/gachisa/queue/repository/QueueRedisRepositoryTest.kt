package com.gachisa.queue.repository

import com.gachisa.queue.dto.ExpiredAdmission
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

class QueueRedisRepositoryTest {
    private var connectionFactory: LettuceConnectionFactory? = null
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var queueRepository: QueueRedisRepository
    private var groupBuyId: Long = 0
    @BeforeEach fun setUp() {
        assumeTrue(isRedisRunning(), "로컬 Redis가 실행 중일 때만 검증합니다.")
        connectionFactory = LettuceConnectionFactory("localhost", 6379).also { it.afterPropertiesSet(); it.start() }
        redisTemplate = StringRedisTemplate(connectionFactory!!).also { it.afterPropertiesSet() }
        queueRepository = QueueRedisRepository(redisTemplate); groupBuyId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE)
    }
    @AfterEach fun tearDown() { if (::queueRepository.isInitialized) queueRepository.deleteQueue(groupBuyId); connectionFactory?.destroy() }
    @Test fun usersAreAdmittedInArrivalOrderAndQueueHasTtl() {
        queueRepository.enqueue(groupBuyId, 101L, "token-101"); queueRepository.enqueue(groupBuyId, 102L, "token-102")
        assertThat(queueRepository.getWaitingPosition(groupBuyId, 101L)).isEqualTo(1L); assertThat(queueRepository.getWaitingPosition(groupBuyId, 102L)).isEqualTo(2L)
        queueRepository.admit(groupBuyId, 1, 10, Instant.now().plusSeconds(60))
        assertThat(queueRepository.getAdmissionExpiresAt(groupBuyId, 101L)).isNotNull(); assertThat(queueRepository.getWaitingPosition(groupBuyId, 102L)).isEqualTo(1L)
        assertThat(redisTemplate.getExpire("queue:waiting:$groupBuyId")).isPositive().isLessThanOrEqualTo(Duration.ofHours(24).seconds)
    }
    @Test fun expiredAdmissionReturnsToEndOfQueueWithPaymentAttempt() {
        val expiresAt = Instant.now().plusSeconds(1); queueRepository.enqueue(groupBuyId, 101L, "token-101"); queueRepository.enqueue(groupBuyId, 102L, "token-102"); queueRepository.admit(groupBuyId, 1, 1, expiresAt); queueRepository.bindPaymentAttempt(groupBuyId, 101L, 9001L)
        assertThat(queueRepository.requeueExpired(groupBuyId, expiresAt.plusSeconds(1))).containsExactly(ExpiredAdmission(101L, 9001L)); assertThat(queueRepository.getWaitingPosition(groupBuyId, 102L)).isEqualTo(1L); assertThat(queueRepository.getWaitingPosition(groupBuyId, 101L)).isEqualTo(2L)
    }
    private fun isRedisRunning(): Boolean = try { Socket().use { it.connect(InetSocketAddress("localhost", 6379), 300); true } } catch (exception: Exception) { false }
}
