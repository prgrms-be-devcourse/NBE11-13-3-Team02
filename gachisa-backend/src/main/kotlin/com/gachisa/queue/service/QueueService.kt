package com.gachisa.queue.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.groupbuy.dto.GroupBuyQueueInfo
import com.gachisa.groupbuy.service.GroupBuyService
import com.gachisa.queue.dto.QueueState
import com.gachisa.queue.dto.QueueStatusResponse
import com.gachisa.queue.dto.QueueTokenResponse
import com.gachisa.queue.event.QueueAdmissionExpiredEvent
import com.gachisa.queue.metric.PaymentQueueMetrics
import com.gachisa.queue.repository.QueueRedisRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class QueueService(
    private val queueRepository: QueueRedisRepository,
    private val groupBuyService: GroupBuyService,
    private val timeProvider: TimeProvider,
    private val eventPublisher: ApplicationEventPublisher,
    private val paymentQueueMetrics: PaymentQueueMetrics,
) {
    fun issueToken(groupBuyId: Long, userId: Long): QueueTokenResponse {
        val groupBuy = getOpenGroupBuy(groupBuyId)
        if (queueRepository.enqueue(groupBuyId, userId, UUID.randomUUID().toString())) {
            paymentQueueMetrics.record("enqueued", 1.0)
        }
        admitAvailable(groupBuy)
        val status = getStatusInternal(groupBuyId, userId)
        return QueueTokenResponse(queueRepository.getToken(groupBuyId, userId)!!, status.status, status.position, status.admissionExpiresAt)
    }

    fun getStatus(groupBuyId: Long, userId: Long, queueToken: String): QueueStatusResponse {
        validateToken(groupBuyId, userId, queueToken)
        val groupBuy = getOpenGroupBuy(groupBuyId)
        processExpired(groupBuy)
        admitAvailable(groupBuy)
        return getStatusInternal(groupBuyId, userId)
    }

    fun requireAdmission(groupBuyId: Long, userId: Long, queueToken: String) {
        validateToken(groupBuyId, userId, queueToken)
        val expiresAt = queueRepository.getAdmissionExpiresAt(groupBuyId, userId)
            ?: throw CustomException(ErrorCode.QUEUE_ADMISSION_REQUIRED)
        if (!expiresAt.isAfter(nowInstant())) throw CustomException(ErrorCode.QUEUE_ADMISSION_EXPIRED)
    }

    fun bindPaymentAttempt(groupBuyId: Long, userId: Long, paymentAttemptId: Long) = queueRepository.bindPaymentAttempt(groupBuyId, userId, paymentAttemptId)
    fun startConfirmation(groupBuyId: Long, userId: Long) {
        if (!queueRepository.startConfirmation(groupBuyId, userId, nowInstant())) throw CustomException(ErrorCode.QUEUE_ADMISSION_EXPIRED)
        paymentQueueMetrics.record("confirmation_started", 1.0)
    }
    fun confirmationFailed(groupBuyId: Long, userId: Long) {
        if (queueRepository.requeueConfirmation(groupBuyId, userId)) paymentQueueMetrics.record("requeued_confirmation", 1.0)
    }
    fun completeAdmission(groupBuyId: Long, userId: Long) {
        queueRepository.complete(groupBuyId, userId)
        paymentQueueMetrics.record("completed", 1.0)
    }

    fun processAllQueues() {
        queueRepository.getGroupBuyIds().forEach { groupBuyId ->
            val groupBuy = groupBuyService.getQueueInfo(groupBuyId.toLong())
            if (groupBuy.isOpen(timeProvider.now())) {
                processExpired(groupBuy)
                admitAvailable(groupBuy)
            } else queueRepository.deleteQueue(groupBuyId.toLong())
        }
    }

    private fun processExpired(groupBuy: GroupBuyQueueInfo) {
        val expiredAdmissions = queueRepository.requeueExpired(groupBuy.groupBuyId(), nowInstant())
        paymentQueueMetrics.record("expired", expiredAdmissions.size.toDouble())
        expiredAdmissions.forEach { expired ->
            expired.paymentAttemptId?.let { eventPublisher.publishEvent(QueueAdmissionExpiredEvent(it)) }
        }
    }

    private fun admitAvailable(groupBuy: GroupBuyQueueInfo) {
        val admitted = queueRepository.admit(
            groupBuy.groupBuyId(),
            minOf(groupBuy.remainingCount(), MAX_CONCURRENT_PAYMENT_ADMISSIONS),
            ADMISSION_BATCH_SIZE,
            nowInstant().plus(ADMISSION_TIMEOUT),
        )
        paymentQueueMetrics.record("admitted", admitted.toDouble())
    }

    private fun getStatusInternal(groupBuyId: Long, userId: Long): QueueStatusResponse {
        val expiresAt = queueRepository.getAdmissionExpiresAt(groupBuyId, userId)
        if (expiresAt != null) return QueueStatusResponse(QueueState.ADMITTED, null, toLocalDateTime(expiresAt))
        if (queueRepository.isConfirming(groupBuyId, userId)) return QueueStatusResponse(QueueState.CONFIRMING, null, null)
        val position = queueRepository.getWaitingPosition(groupBuyId, userId) ?: throw CustomException(ErrorCode.QUEUE_TOKEN_INVALID)
        return QueueStatusResponse(QueueState.WAITING, position, null)
    }

    private fun getOpenGroupBuy(groupBuyId: Long): GroupBuyQueueInfo {
        val groupBuy = groupBuyService.getQueueInfo(groupBuyId)
        if (!groupBuy.isOpen(timeProvider.now()) || groupBuy.remainingCount() == 0) throw CustomException(ErrorCode.QUEUE_NOT_OPEN)
        return groupBuy
    }

    private fun validateToken(groupBuyId: Long, userId: Long, queueToken: String) {
        if (queueToken != queueRepository.getToken(groupBuyId, userId)) throw CustomException(ErrorCode.QUEUE_TOKEN_INVALID)
    }

    private fun nowInstant(): Instant = timeProvider.now().atZone(SEOUL).toInstant()
    private fun toLocalDateTime(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, SEOUL)

    companion object {
        private val ADMISSION_TIMEOUT: Duration = Duration.ofMinutes(10)
        private const val MAX_CONCURRENT_PAYMENT_ADMISSIONS = 10
        private const val ADMISSION_BATCH_SIZE = 10
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
