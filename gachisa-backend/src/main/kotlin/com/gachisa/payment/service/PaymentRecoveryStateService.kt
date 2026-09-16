package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.order.dto.OrderCreateCommand
import com.gachisa.order.service.OrderService
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.service.dto.RecoveryPreparation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentRecoveryStateService(
    private val paymentRepository: PaymentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val participationService: ParticipationService,
    private val refundCompletionService: RefundCompletionService,
    private val timeProvider: TimeProvider,
    private val retryPolicy: PgRetryPolicy,
    private val orderService: OrderService,
) {
    @Transactional
    fun prepare(attemptId: Long): RecoveryPreparation {
        val target = getForUpdate(attemptId)
        val attempt = target.attempt
        if (attempt.status != PaymentAttemptStatus.PROCESSING) return RecoveryPreparation.skip(target.payment, attempt)
        if (attempt.pgPaymentKey == null) throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
        if (retryPolicy.isExhausted(attempt.retryCount)) {
            attempt.exhaustRetry("PAYMENT_RECOVERY_EXHAUSTED", "결제 상태 자동 확인 횟수를 초과했습니다.", timeProvider.now())
            return RecoveryPreparation.skip(target.payment, attempt)
        }
        val now = timeProvider.now()
        attempt.recordRecoveryAttempt(now, retryPolicy.nextRetryAt(now, attempt.retryCount + 2))
        return RecoveryPreparation.query(attempt)
    }

    @Transactional
    fun apply(attemptId: Long, result: PgPaymentQueryResult): PaymentResponse {
        val target = getForUpdate(attemptId)
        validatePayment(target.payment, target.attempt, result)
        val orderId = when (result.status) {
            "DONE" -> complete(target.payment, target.attempt)
            "CANCELED" -> { refundCompletionService.reconcileCancellation(target.payment, result.cancellationReason, result.cancellationTransactionKey, result.cancelledAmount); null }
            "ABORTED" -> { target.attempt.fail("TOSS_PAYMENT_ABORTED", "토스 결제 승인이 실패했습니다.", timeProvider.now()); null }
            "EXPIRED" -> { target.attempt.expire(timeProvider.now()); null }
            else -> { recordUnknownStatus(target.attempt, result.status); null }
        }
        return PaymentResponse.from(target.payment, target.attempt, orderId)
    }

    @Transactional
    fun recordFailure(attemptId: Long, errorCode: ErrorCode) {
        val attempt = getForUpdate(attemptId).attempt
        if (attempt.status != PaymentAttemptStatus.PROCESSING) return
        if (retryPolicy.isExhausted(attempt.retryCount)) attempt.exhaustRetry(errorCode.name, errorCode.getMessage(), timeProvider.now())
        else attempt.recordRecoveryFailure(errorCode.name, errorCode.getMessage(), timeProvider.now())
    }

    @Transactional(readOnly = true)
    fun findAttemptIdByPgOrderId(pgOrderId: String): Long = paymentAttemptRepository.findByPgOrderId(pgOrderId)
        .map { it.id!! }
        .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }

    private fun complete(payment: Payment, attempt: PaymentAttempt): Long {
        if (attempt.status != PaymentAttemptStatus.PAID) {
            if (payment.status != PaymentStatus.READY || attempt.status != PaymentAttemptStatus.PROCESSING) throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION)
            participationService.confirmPayment(payment.participationId)
            payment.complete(timeProvider.now())
            attempt.complete(timeProvider.now())
        }
        val participation = participationService.getPaymentInfo(payment.participationId)
        return orderService.createOrderIfAbsent(OrderCreateCommand(payment.participationId, payment.id!!, participation.userId(), participation.groupBuyId(), participation.quantity(), payment.amount)).orderId
    }

    private fun validatePayment(payment: Payment, attempt: PaymentAttempt, result: PgPaymentQueryResult) {
        if (attempt.pgOrderId != result.pgOrderId || payment.amount != result.amount || attempt.pgPaymentKey != result.paymentKey) throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
    }

    private fun recordUnknownStatus(attempt: PaymentAttempt, status: String) {
        val message = "확인되지 않은 Toss 결제 상태입니다: $status"
        if (retryPolicy.isExhausted(attempt.retryCount)) attempt.exhaustRetry("TOSS_PAYMENT_STATUS_UNKNOWN", message, timeProvider.now())
        else attempt.recordRecoveryFailure("TOSS_PAYMENT_STATUS_UNKNOWN", message, timeProvider.now())
    }

    private fun getForUpdate(attemptId: Long): PaymentAndAttempt {
        val paymentId = paymentAttemptRepository.findPaymentIdByAttemptId(attemptId).orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }
        val payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }
        val attempt = paymentAttemptRepository.findByIdForUpdate(attemptId).orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }
        return PaymentAndAttempt(payment, attempt)
    }

    private data class PaymentAndAttempt(val payment: Payment, val attempt: PaymentAttempt)
}
