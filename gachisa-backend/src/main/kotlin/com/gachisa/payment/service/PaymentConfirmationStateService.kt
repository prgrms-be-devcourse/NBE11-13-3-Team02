package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.order.dto.OrderCreateCommand
import com.gachisa.order.dto.OrderResponse
import com.gachisa.order.service.OrderService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.client.dto.PgConfirmationResult
import com.gachisa.payment.dto.PaymentConfirmRequest
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.service.dto.ConfirmationPreparation
import com.gachisa.queue.service.QueueService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentConfirmationStateService(
    private val paymentRepository: PaymentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val participationService: ParticipationService,
    private val timeProvider: TimeProvider,
    private val queueService: QueueService,
    private val orderService: OrderService,
) {
    @Transactional
    fun prepare(attemptId: Long, request: PaymentConfirmRequest): ConfirmationPreparation {
        val target = getForUpdate(attemptId)
        validateRequest(target.payment, target.attempt, request)
        if (target.attempt.status == PaymentAttemptStatus.PAID) {
            val participation = participationService.getPaymentInfo(target.payment.participationId)
            return ConfirmationPreparation.existing(target.payment, target.attempt, createOrder(target.payment, participation).orderId)
        }
        if (target.attempt.status == PaymentAttemptStatus.PROCESSING) {
            if (request.paymentKey != target.attempt.pgPaymentKey) throw CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED)
            return ConfirmationPreparation.existing(target.payment, target.attempt)
        }
        if (target.payment.status != PaymentStatus.READY || target.attempt.status != PaymentAttemptStatus.READY) {
            throw CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED)
        }
        if (target.attempt.isExpired(timeProvider.now())) {
            target.attempt.expire(timeProvider.now())
            throw CustomException(ErrorCode.PAYMENT_EXPIRED)
        }

        val participation = participationService.getPaymentInfo(target.payment.participationId)
        queueService.startConfirmation(participation.groupBuyId(), participation.userId())
        target.attempt.beginConfirmation(request.paymentKey, timeProvider.now())
        return ConfirmationPreparation.request(target.payment, target.attempt)
    }

    @Transactional
    fun complete(attemptId: Long, result: PgConfirmationResult): PaymentResponse {
        val target = getForUpdate(attemptId)
        val payment = target.payment
        val attempt = target.attempt
        if (attempt.status == PaymentAttemptStatus.PAID) {
            val participation = participationService.getPaymentInfo(payment.participationId)
            return PaymentResponse.from(payment, attempt, createOrder(payment, participation).orderId)
        }
        if (payment.status != PaymentStatus.READY || attempt.status != PaymentAttemptStatus.PROCESSING ||
            attempt.pgOrderId != result.pgOrderId || payment.amount != result.amount ||
            attempt.pgPaymentKey != result.pgTransactionId
        ) throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)

        val participation = participationService.getPaymentInfo(payment.participationId)
        participationService.confirmPayment(payment.participationId)
        payment.complete(timeProvider.now())
        attempt.complete(timeProvider.now())
        return PaymentResponse.from(payment, attempt, createOrder(payment, participation).orderId)
    }

    @Transactional
    fun fail(attemptId: Long, errorCode: ErrorCode) {
        val attempt = getForUpdate(attemptId).attempt
        if (attempt.status == PaymentAttemptStatus.PROCESSING) attempt.fail(errorCode.name, errorCode.getMessage(), timeProvider.now())
    }

    @Transactional
    fun keepProcessing(attemptId: Long, errorCode: ErrorCode) {
        val attempt = getForUpdate(attemptId).attempt
        if (attempt.status == PaymentAttemptStatus.PROCESSING) {
            attempt.recordRecoveryFailure(errorCode.name, errorCode.getMessage(), timeProvider.now())
        }
    }

    private fun createOrder(payment: Payment, participation: ParticipationPaymentInfo): OrderResponse =
        orderService.createOrderIfAbsent(OrderCreateCommand(payment.participationId, payment.id!!, participation.userId(), participation.groupBuyId(), participation.quantity(), payment.amount))

    private fun getForUpdate(attemptId: Long): PaymentAndAttempt {
        val paymentId = paymentAttemptRepository.findPaymentIdByAttemptId(attemptId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }
        val attempt = paymentAttemptRepository.findByIdForUpdate(attemptId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }
        return PaymentAndAttempt(payment, attempt)
    }

    private fun validateRequest(payment: Payment, attempt: PaymentAttempt, request: PaymentConfirmRequest) {
        if (attempt.pgOrderId != request.pgOrderId) throw CustomException(ErrorCode.PAYMENT_ORDER_MISMATCH)
        if (payment.amount != request.amount) throw CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)
    }

    private data class PaymentAndAttempt(val payment: Payment, val attempt: PaymentAttempt)
}
