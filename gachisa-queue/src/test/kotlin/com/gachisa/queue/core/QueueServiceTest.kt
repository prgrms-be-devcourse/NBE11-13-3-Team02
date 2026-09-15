package com.gachisa.queue.core

import com.gachisa.queue.client.CoreClient
import com.gachisa.queue.redis.ExpiredAdmission
import com.gachisa.queue.redis.QueueRedisRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.springframework.web.server.ResponseStatusException

class QueueServiceTest {

    private val now: Instant = Instant.parse("2026-09-15T03:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"))
    private val properties = QueueProperties(
        jwtSecret = "x".repeat(64),
        internalToken = "internal",
        coreBaseUrl = "http://core.test",
        admissionTimeout = Duration.ofMinutes(10),
        admissionBatchSize = 10,
    )

    private fun openGroupBuy() = GroupBuyQueueInfo(
        groupBuyId = 1L,
        targetCount = 20,
        currentCount = 5,
        openAt = java.time.LocalDateTime.of(2026, 9, 1, 0, 0),
        deadline = java.time.LocalDateTime.of(2026, 12, 1, 0, 0),
        status = "RECRUITING",
    )

    @Test
    fun `모집중이 아니면 토큰을 발급하지 않는다`() = runTest {
        val queue = mock<QueueRedisRepository>()
        val core = mock<CoreClient> {
            onBlocking { getQueueInfo(1L) } doReturn openGroupBuy().copy(status = "CANCELLED")
        }
        val service = QueueService(queue, core, properties, clock)

        val error = assertThrows<ResponseStatusException> { service.issueToken(1L, 7L) }

        assertTrue(error.message.contains("QUEUE_NOT_OPEN"))
        verify(queue, never()).enqueue(any(), any(), any())
    }

    @Test
    fun `목표 인원이 다 찼으면 대기열을 열지 않는다`() = runTest {
        val queue = mock<QueueRedisRepository>()
        val core = mock<CoreClient> {
            onBlocking { getQueueInfo(1L) } doReturn openGroupBuy().copy(currentCount = 20)
        }
        val service = QueueService(queue, core, properties, clock)

        assertThrows<ResponseStatusException> { service.issueToken(1L, 7L) }
    }

    @Test
    fun `입장 기록이 없으면 결제를 시작할 수 없다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getToken(1L, 7L) } doReturn "tok"
            onBlocking { getAdmissionExpiresAt(1L, 7L) } doReturn null
        }
        val service = QueueService(queue, mock(), properties, clock)

        val error = assertThrows<ResponseStatusException> { service.requireAdmission(1L, 7L, "tok") }

        assertTrue(error.message.contains("QUEUE_ADMISSION_REQUIRED"))
    }

    @Test
    fun `입장 시간이 지났으면 만료로 거절한다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getToken(1L, 7L) } doReturn "tok"
            onBlocking { getAdmissionExpiresAt(1L, 7L) } doReturn now.minusSeconds(1)
        }
        val service = QueueService(queue, mock(), properties, clock)

        val error = assertThrows<ResponseStatusException> { service.requireAdmission(1L, 7L, "tok") }

        assertTrue(error.message.contains("QUEUE_ADMISSION_EXPIRED"))
    }

    @Test
    fun `토큰이 다르면 남의 대기열 상태를 볼 수 없다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getToken(1L, 7L) } doReturn "real-token"
        }
        val service = QueueService(queue, mock(), properties, clock)

        val error = assertThrows<ResponseStatusException> { service.getStatus(1L, 7L, "guessed") }

        assertTrue(error.message.contains("QUEUE_TOKEN_INVALID"))
    }

    @Test
    fun `대기 중이면 순번을 돌려준다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getToken(1L, 7L) } doReturn "tok"
            onBlocking { getAdmissionExpiresAt(1L, 7L) } doReturn null
            onBlocking { isConfirming(1L, 7L) } doReturn false
            onBlocking { getWaitingPosition(1L, 7L) } doReturn 3L
            onBlocking { requeueExpired(any(), any()) } doReturn emptyList()
        }
        val core = mock<CoreClient> { onBlocking { getQueueInfo(1L) } doReturn openGroupBuy() }
        val service = QueueService(queue, core, properties, clock)

        val status = service.getStatus(1L, 7L, "tok")

        assertEquals(QueueState.WAITING, status.status)
        assertEquals(3L, status.position)
    }

    @Test
    fun `startConfirmation이 거절되면 만료 예외를 던진다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { startConfirmation(1L, 7L, now) } doReturn false
        }
        val service = QueueService(queue, mock(), properties, clock)

        assertThrows<ResponseStatusException> { service.startConfirmation(1L, 7L) }
    }

    /**
     * 만료 통지는 서로 독립적이므로 동시에 나가야 한다. 순차로 보내면 지연이 합산돼
     * 뒤에 있는 사용자의 입장 처리가 그만큼 밀린다.
     */
    @Test
    fun `만료 통지를 동시에 보낸다`() = runTest {
        val inFlight = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val queue = mock<QueueRedisRepository> {
            onBlocking { getGroupBuyIds() } doReturn listOf(1L)
            onBlocking { requeueExpired(1L, now) } doReturn (1L..5L).map { ExpiredAdmission(it, it * 100) }
        }
        val core = mock<CoreClient> {
            onBlocking { getQueueInfo(1L) } doReturn openGroupBuy()
        }
        core.stub {
            onBlocking { expirePaymentAttempt(any()) } doSuspendableAnswer {
                maxConcurrent.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                delay(50)
                inFlight.decrementAndGet()
                true
            }
        }
        val service = QueueService(queue, core, properties, clock)

        service.processAllQueues()

        assertEquals(5, maxConcurrent.get(), "5건이 동시에 나가야 한다")
    }

    /** core 통지가 실패해도 대기열 진행은 멈추면 안 된다. core에 복구 스케줄러가 따로 있다. */
    @Test
    fun `만료 통지가 실패해도 입장 처리를 계속한다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getGroupBuyIds() } doReturn listOf(1L)
            onBlocking { requeueExpired(1L, now) } doReturn listOf(ExpiredAdmission(7L, 100L))
        }
        val core = mock<CoreClient> {
            onBlocking { getQueueInfo(1L) } doReturn openGroupBuy()
            onBlocking { expirePaymentAttempt(100L) } doReturn false
        }
        val service = QueueService(queue, core, properties, clock)

        service.processAllQueues()

        verify(queue).admit(eq(1L), any(), any(), any())
    }

    /** 마감된 공동구매의 대기열은 지워 Redis에 쌓이지 않게 한다. */
    @Test
    fun `닫힌 공동구매의 대기열은 삭제한다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getGroupBuyIds() } doReturn listOf(1L)
        }
        val core = mock<CoreClient> {
            onBlocking { getQueueInfo(1L) } doReturn openGroupBuy().copy(status = "ACHIEVED")
        }
        val service = QueueService(queue, core, properties, clock)

        service.processAllQueues()

        verify(queue).deleteQueue(1L)
        verify(queue, never()).admit(any(), any(), any(), any())
    }

    /** core가 죽어도 다른 대기열 처리를 멈추지 않는다. */
    @Test
    fun `공동구매 조회 실패는 건너뛴다`() = runTest {
        val queue = mock<QueueRedisRepository> {
            onBlocking { getGroupBuyIds() } doReturn listOf(1L)
        }
        val core = mock<CoreClient> {
            onBlocking { getQueueInfo(1L) } doSuspendableAnswer { throw RuntimeException("core down") }
        }
        val service = QueueService(queue, core, properties, clock)

        service.processAllQueues()

        verify(queue, never()).deleteQueue(any())
        verify(queue, never()).admit(any(), any(), any(), any())
    }
}
