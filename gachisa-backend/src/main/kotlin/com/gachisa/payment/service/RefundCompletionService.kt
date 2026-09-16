package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.order.service.OrderService
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.entity.Refund
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.repository.RefundRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.time.LocalDateTime
import java.util.UUID

@Service
class RefundCompletionService(
    private val paymentRepository: PaymentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val refundRepository: RefundRepository,
    private val participationService: ParticipationService,
    private val timeProvider: TimeProvider,
    private val orderService: OrderService,
) {
    @Transactional
    fun complete(refundId: Long, result: PgCancellationResult): RefundResponse {
        val snapshot = getRefund(refundId)
        val payment = paymentRepository.findByIdForUpdate(snapshot.paymentId).orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }
        val refund = refundRepository.findByIdForUpdate(refundId).orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }
        if (refund.status == RefundStatus.REFUNDED) return RefundResponse.from(refund)
        validateCancellation(getPaidAttempt(payment.id!!), refund, result)
        val now = timeProvider.now()
        payment.refund(now)
        refund.complete(result.cancellationTransactionKey, now)
        synchronizeParticipationRefund(payment.participationId)
        orderService.reflectRefund(payment.id!!)
        return RefundResponse.from(refund)
    }

    @Transactional
    fun reconcileCancellation(payment: Payment, reason: String?, transactionId: String?, cancelledAmount: Int) {
        if (cancelledAmount != payment.amount) throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
        val now = timeProvider.now()
        var refund = refundRepository.findByPaymentIdForUpdate(payment.id!!).orElse(null)
        if (refund == null) refund = createRefund(payment, reason, now)
        if (refund.status != RefundStatus.REFUNDED) refund.complete(transactionId, now)
        if (payment.status != PaymentStatus.REFUNDED) payment.refund(now)
        synchronizeParticipationRefund(payment.participationId)
        orderService.reflectRefund(payment.id!!)
    }

    private fun createRefund(payment: Payment, reason: String?, now: LocalDateTime): Refund = refundRepository.save(
        Refund(
            paymentId = payment.id!!,
            amount = payment.amount,
            reason = if (StringUtils.hasText(reason)) reason!! else "토스 결제 취소 상태 동기화",
            status = RefundStatus.REFUND_PENDING,
            pgIdempotencyKey = UUID.randomUUID().toString(),
            requestedAt = now,
            updatedAt = now,
        ),
    )

    private fun synchronizeParticipationRefund(participationId: Long) {
        if (participationService.getPaymentInfo(participationId).payable()) participationService.confirmPayment(participationId)
        participationService.refundPayment(participationId)
    }

    private fun getRefund(refundId: Long): Refund = refundRepository.findById(refundId)
        .orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }

    private fun getPaidAttempt(paymentId: Long): PaymentAttempt = paymentAttemptRepository
        .findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(paymentId, PaymentAttemptStatus.PAID)
        .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }

    private fun validateCancellation(attempt: PaymentAttempt, refund: Refund, result: PgCancellationResult) {
        if (attempt.pgPaymentKey != result.paymentKey || attempt.pgOrderId != result.pgOrderId || refund.amount != result.cancelledAmount) {
            throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
        }
    }
}
