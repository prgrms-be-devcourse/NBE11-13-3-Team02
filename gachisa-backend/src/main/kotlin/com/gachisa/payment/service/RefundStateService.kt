package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.entity.Refund
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.repository.RefundRepository
import com.gachisa.payment.service.dto.RefundPreparation
import com.gachisa.payment.service.dto.RefundRecoveryTarget
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.util.UUID

@Service
class RefundStateService(
    private val paymentRepository: PaymentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val refundRepository: RefundRepository,
    private val timeProvider: TimeProvider,
    private val retryPolicy: PgRetryPolicy,
) {
    @Transactional
    fun prepare(paymentId: Long, reason: String): RefundPreparation {
        validateReason(reason)
        val payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }
        val existingRefund = refundRepository.findByPaymentIdForUpdate(paymentId).orElse(null)
        if (existingRefund != null) {
            if (existingRefund.status == RefundStatus.FAILED) {
                existingRefund.retry(timeProvider.now())
                return RefundPreparation.request(existingRefund, getPaidAttempt(payment.id!!))
            }
            return RefundPreparation.existing(existingRefund)
        }
        if (payment.status != PaymentStatus.PAID) throw CustomException(ErrorCode.REFUND_NOT_ALLOWED)
        val now = timeProvider.now()
        val refund = Refund(
            paymentId = payment.id!!,
            amount = payment.amount,
            reason = reason,
            status = RefundStatus.REFUND_PENDING,
            pgIdempotencyKey = UUID.randomUUID().toString(),
            requestedAt = now,
            updatedAt = now,
        )
        return RefundPreparation.request(refundRepository.saveAndFlush(refund), getPaidAttempt(payment.id!!))
    }

    @Transactional
    fun claimPending(refundId: Long): RefundPreparation {
        val refund = refundRepository.findByIdForUpdate(refundId).orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }
        if (refund.status != RefundStatus.REFUND_PENDING) return RefundPreparation.existing(refund)
        val paidAttempt = getPaidAttempt(refund.paymentId)
        refund.startProcessing(timeProvider.now())
        return RefundPreparation.request(refund, paidAttempt)
    }

    @Transactional
    fun keepPending(refundId: Long, errorCode: ErrorCode) {
        val refund = refundRepository.findByIdForUpdate(refundId).orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }
        if (refund.status == RefundStatus.REFUNDED) return
        if (retryPolicy.isExhausted(refund.retryCount)) {
            refund.exhaustRetry(errorCode.name, errorCode.getMessage(), timeProvider.now())
            return
        }
        val now = timeProvider.now()
        refund.scheduleRetry(errorCode.name, errorCode.getMessage(), now, retryPolicy.nextRetryAt(now, refund.retryCount + 1))
    }

    @Transactional
    fun retryPending(refundId: Long) {
        val refund = refundRepository.findByIdForUpdate(refundId).orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }
        if (refund.status != RefundStatus.REFUNDED) refund.resume(timeProvider.now())
    }

    @Transactional(readOnly = true)
    fun getRecoveryTarget(refundId: Long): RefundRecoveryTarget {
        val refund = getRefundEntity(refundId)
        val paidAttempt = getPaidAttempt(refund.paymentId)
        return RefundRecoveryTarget(refund.id!!, paidAttempt.pgPaymentKey!!, paidAttempt.pgOrderId, refund.amount)
    }

    @Transactional
    fun fail(refundId: Long, errorCode: ErrorCode) {
        val refund = refundRepository.findByIdForUpdate(refundId).orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }
        if (refund.status != RefundStatus.REFUNDED) refund.fail(errorCode.name, errorCode.getMessage(), timeProvider.now())
    }

    @Transactional(readOnly = true)
    fun getRefund(refundId: Long): RefundResponse = RefundResponse.from(getRefundEntity(refundId))

    private fun getRefundEntity(refundId: Long): Refund = refundRepository.findById(refundId)
        .orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }

    private fun validateReason(reason: String) {
        if (!StringUtils.hasText(reason) || reason.length > 200) throw CustomException(ErrorCode.REFUND_REASON_REQUIRED)
    }

    private fun getPaidAttempt(paymentId: Long): PaymentAttempt = paymentAttemptRepository
        .findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(paymentId, PaymentAttemptStatus.PAID)
        .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }
}
