package com.gachisa.queue.core

import com.gachisa.queue.client.CoreClient
import com.gachisa.queue.redis.QueueRedisRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QueueService(
    private val queue: QueueRedisRepository,
    private val core: CoreClient,
    private val properties: QueueProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun issueToken(groupBuyId: Long, userId: Long): QueueTokenResponse {
        val groupBuy = requireOpen(groupBuyId)
        queue.enqueue(groupBuyId, userId, UUID.randomUUID().toString())
        admitAvailable(groupBuy)

        val token = queue.getToken(groupBuyId, userId)
        val status = statusOf(groupBuyId, userId)
        return QueueTokenResponse(token, status.status, status.position, status.admissionExpiresAt)
    }

    suspend fun getStatus(groupBuyId: Long, userId: Long, queueToken: String): QueueStatusResponse {
        validateToken(groupBuyId, userId, queueToken)
        val groupBuy = requireOpen(groupBuyId)
        processExpired(groupBuy)
        admitAvailable(groupBuy)
        return statusOf(groupBuyId, userId)
    }

    suspend fun requireAdmission(groupBuyId: Long, userId: Long, queueToken: String) {
        validateToken(groupBuyId, userId, queueToken)
        val expiresAt = queue.getAdmissionExpiresAt(groupBuyId, userId)
            ?: throw QueueError.QUEUE_ADMISSION_REQUIRED.toException()
        if (!expiresAt.isAfter(now())) {
            throw QueueError.QUEUE_ADMISSION_EXPIRED.toException()
        }
    }

    suspend fun bindPaymentAttempt(groupBuyId: Long, userId: Long, paymentAttemptId: Long) {
        queue.bindPaymentAttempt(groupBuyId, userId, paymentAttemptId)
    }

    suspend fun startConfirmation(groupBuyId: Long, userId: Long) {
        if (!queue.startConfirmation(groupBuyId, userId, now())) {
            throw QueueError.QUEUE_ADMISSION_EXPIRED.toException()
        }
    }

    suspend fun confirmationFailed(groupBuyId: Long, userId: Long) {
        queue.requeueConfirmation(groupBuyId, userId)
    }

    suspend fun completeAdmission(groupBuyId: Long, userId: Long) {
        queue.complete(groupBuyId, userId)
    }

    /** 스케줄러가 부른다. 열려 있는 큐는 정리하고, 닫힌 공동구매의 큐는 지운다. */
    suspend fun processAllQueues() {
        for (groupBuyId in queue.getGroupBuyIds()) {
            val groupBuy = runCatching { core.getQueueInfo(groupBuyId) }.getOrNull()
            if (groupBuy == null) {
                log.warn("공동구매 정보를 가져오지 못해 건너뜁니다: groupBuyId={}", groupBuyId)
                continue
            }
            if (groupBuy.isOpen(localNow())) {
                processExpired(groupBuy)
                admitAvailable(groupBuy)
            } else {
                queue.deleteQueue(groupBuyId)
            }
        }
    }

    private suspend fun processExpired(groupBuy: GroupBuyQueueInfo) {
        val expired = queue.requeueExpired(groupBuy.groupBuyId, now())
        val attemptIds = expired.mapNotNull { it.paymentAttemptId }
        if (attemptIds.isEmpty()) return

        // 만료 통지는 서로 독립적이다. 순차로 보내면 대기 시간이 합이 되므로 동시에 보낸다.
        val failed = coroutineScope {
            attemptIds.map { id -> async { id to core.expirePaymentAttempt(id) } }.awaitAll()
        }.filterNot { it.second }.map { it.first }

        if (failed.isNotEmpty()) {
            log.warn("결제 시도 만료 통지 실패: attemptIds={} (core 복구 스케줄러가 처리)", failed)
        }
    }

    private suspend fun admitAvailable(groupBuy: GroupBuyQueueInfo) {
        queue.admit(
            groupBuy.groupBuyId,
            groupBuy.remainingCount,
            properties.admissionBatchSize,
            now().plus(properties.admissionTimeout),
        )
    }

    private suspend fun statusOf(groupBuyId: Long, userId: Long): QueueStatusResponse {
        queue.getAdmissionExpiresAt(groupBuyId, userId)?.let {
            return QueueStatusResponse(QueueState.ADMITTED, null, it.toLocal())
        }
        if (queue.isConfirming(groupBuyId, userId)) {
            return QueueStatusResponse(QueueState.CONFIRMING, null, null)
        }
        val position = queue.getWaitingPosition(groupBuyId, userId)
            ?: throw QueueError.QUEUE_TOKEN_INVALID.toException()
        return QueueStatusResponse(QueueState.WAITING, position, null)
    }

    private suspend fun requireOpen(groupBuyId: Long): GroupBuyQueueInfo {
        val groupBuy = core.getQueueInfo(groupBuyId)
        if (!groupBuy.isOpen(localNow()) || groupBuy.remainingCount == 0) {
            throw QueueError.QUEUE_NOT_OPEN.toException()
        }
        return groupBuy
    }

    private suspend fun validateToken(groupBuyId: Long, userId: Long, queueToken: String) {
        if (queue.getToken(groupBuyId, userId) != queueToken) {
            throw QueueError.QUEUE_TOKEN_INVALID.toException()
        }
    }

    private fun now(): Instant = clock.instant()
    private fun localNow(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), SEOUL)
    private fun Instant.toLocal(): LocalDateTime = LocalDateTime.ofInstant(this, SEOUL)

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
