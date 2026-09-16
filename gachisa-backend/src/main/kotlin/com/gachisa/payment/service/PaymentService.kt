package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.order.service.OrderService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.dto.PaymentConfirmRequest
import com.gachisa.payment.dto.PaymentRequest
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.queue.service.QueueService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val participationService: ParticipationService,
    private val amountCalculator: PaymentAmountCalculator,
    private val pgClient: PgClient,
    private val confirmationStateService: PaymentConfirmationStateService,
    private val timeProvider: TimeProvider,
    private val queueService: QueueService,
    private val orderService: OrderService,
) {
    companion object {
        private val PAYMENT_TIMEOUT: Duration = Duration.ofMinutes(10)
        private const val PG_ORDER_PREFIX = "gachisa_"
    }

    @Transactional
    fun createPayment(
        participationId: Long,
        userId: Long,
        clientRequestId: String,
        queueToken: String,
        request: PaymentRequest,
    ): PaymentResponse {
        validateClientRequestId(clientRequestId)
        val participation = participationService.getPaymentInfo(participationId)
        validateOwner(participation, userId)
        validatePayable(participation)

        val idempotentAttempt = paymentAttemptRepository.findByClientRequestId(clientRequestId).orElse(null)
        if (idempotentAttempt != null) {
            val payment = getPaymentEntity(idempotentAttempt.paymentId)
            validateSamePaymentRequest(payment, idempotentAttempt, participationId, request)
            return PaymentResponse.from(payment, idempotentAttempt)
        }

        queueService.requireAdmission(participation.groupBuyId(), userId, queueToken)

        val existingPayment = paymentRepository.findByParticipationId(participationId).orElse(null)
        val now = timeProvider.now()
        if (existingPayment == null) {
            paymentRepository.insertReadyIfAbsent(
                participationId,
                amountCalculator.calculate(participation),
                now,
            )
        }

        val payment = paymentRepository.findByParticipationIdForUpdate(participationId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }

        val concurrentlyCreatedAttempt = paymentAttemptRepository.findByClientRequestId(clientRequestId).orElse(null)
        if (concurrentlyCreatedAttempt != null) {
            validateSamePaymentRequest(payment, concurrentlyCreatedAttempt, participationId, request)
            return PaymentResponse.from(payment, concurrentlyCreatedAttempt)
        }

        validateNewAttemptAllowed(payment)
        val activeAttempt = paymentAttemptRepository
            .findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
                payment.id!!,
                listOf(PaymentAttemptStatus.READY, PaymentAttemptStatus.PROCESSING),
            ).orElse(null)
        if (activeAttempt != null) {
            if (activeAttempt.status == PaymentAttemptStatus.PROCESSING) {
                throw CustomException(ErrorCode.PAYMENT_ATTEMPT_IN_PROGRESS)
            }
            if (activeAttempt.paymentMethod == request.paymentMethod) {
                return PaymentResponse.from(payment, activeAttempt)
            }
            activeAttempt.cancel(now)
        }

        val attempt = PaymentAttempt(
            paymentId = payment.id!!,
            clientRequestId = clientRequestId,
            pgIdempotencyKey = UUID.randomUUID().toString(),
            pgOrderId = createPgOrderId(),
            paymentMethod = request.paymentMethod,
            status = PaymentAttemptStatus.READY,
            retryCount = 0,
            expiresAt = now.plus(PAYMENT_TIMEOUT),
            createdAt = now,
            updatedAt = now,
        )
        val savedAttempt = paymentAttemptRepository.save(attempt)
        queueService.bindPaymentAttempt(participation.groupBuyId(), userId, savedAttempt.id!!)
        return PaymentResponse.from(payment, savedAttempt)
    }

    fun confirmPayment(paymentAttemptId: Long, userId: Long, request: PaymentConfirmRequest): PaymentResponse {
        val attempt = getPaymentAttempt(paymentAttemptId)
        val payment = getPaymentEntity(attempt.paymentId)
        val participation = participationService.getPaymentInfo(payment.participationId)
        validateOwner(participation, userId)

        val preparation = confirmationStateService.prepare(paymentAttemptId, request)
        if (!preparation.requestRequired) {
            val existingResponse = preparation.existingResponse!!
            if (existingResponse.paymentStatus == PaymentStatus.PAID) {
                queueService.completeAdmission(participation.groupBuyId(), userId)
            }
            return existingResponse
        }

        try {
            val result = pgClient.confirm(
                preparation.paymentKey,
                preparation.pgOrderId,
                preparation.amount,
                preparation.pgIdempotencyKey,
                preparation.paymentMethod,
            )
            val response = confirmationStateService.complete(paymentAttemptId, result)
            queueService.completeAdmission(participation.groupBuyId(), userId)
            return response
        } catch (exception: CustomException) {
            if (exception.getErrorCode() == ErrorCode.PAYMENT_GATEWAY_REJECTED) {
                confirmationStateService.fail(paymentAttemptId, exception.getErrorCode())
                queueService.confirmationFailed(participation.groupBuyId(), userId)
            } else {
                confirmationStateService.keepProcessing(paymentAttemptId, exception.getErrorCode())
            }
            throw exception
        }
    }

    fun confirmPaymentByPgOrderId(userId: Long, request: PaymentConfirmRequest): PaymentResponse {
        val paymentAttemptId = paymentAttemptRepository.findByPgOrderId(request.pgOrderId)
            .map { it.id }
            .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }!!
        return confirmPayment(paymentAttemptId, userId, request)
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long, userId: Long): PaymentResponse {
        val payment = getPaymentEntity(paymentId)
        val participation = participationService.getPaymentInfo(payment.participationId)
        validateOwner(participation, userId)
        val attempt = paymentAttemptRepository.findFirstByPaymentIdOrderByCreatedAtDesc(paymentId).orElse(null)
        val orderId = if (payment.status == PaymentStatus.PAID) {
            orderService.getOrderIdByParticipationId(payment.participationId)
        } else {
            null
        }
        return PaymentResponse.from(payment, attempt, orderId)
    }

    @Transactional(readOnly = true)
    fun getPaymentByPgOrderId(pgOrderId: String, userId: Long): PaymentResponse {
        val attempt = paymentAttemptRepository.findByPgOrderId(pgOrderId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }
        val payment = getPaymentEntity(attempt.paymentId)
        val participation = participationService.getPaymentInfo(payment.participationId)
        validateOwner(participation, userId)
        val orderId = if (payment.status == PaymentStatus.PAID) {
            orderService.getOrderIdByParticipationId(payment.participationId)
        } else {
            null
        }
        return PaymentResponse.from(payment, attempt, orderId)
    }

    private fun getPaymentEntity(paymentId: Long): Payment = paymentRepository.findById(paymentId)
        .orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }

    private fun getPaymentAttempt(paymentAttemptId: Long): PaymentAttempt =
        paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND) }

    private fun validateOwner(participation: ParticipationPaymentInfo, userId: Long) {
        if (participation.userId() != userId) throw CustomException(ErrorCode.FORBIDDEN)
    }

    private fun validatePayable(participation: ParticipationPaymentInfo) {
        if (!participation.payable()) throw CustomException(ErrorCode.PAYMENT_NOT_ALLOWED)
    }

    private fun validateClientRequestId(clientRequestId: String) {
        try {
            val uuid = UUID.fromString(clientRequestId)
            if (uuid.version() != 4 || !uuid.toString().equals(clientRequestId, ignoreCase = true)) {
                throw IllegalArgumentException()
            }
        } catch (exception: IllegalArgumentException) {
            throw CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_INVALID)
        }
    }

    private fun validateSamePaymentRequest(
        payment: Payment,
        attempt: PaymentAttempt,
        participationId: Long,
        request: PaymentRequest,
    ) {
        if (payment.participationId != participationId || attempt.paymentMethod != request.paymentMethod) {
            throw CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT)
        }
    }

    private fun validateNewAttemptAllowed(payment: Payment) {
        if (payment.status != PaymentStatus.READY) {
            throw CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED)
        }
    }

    private fun createPgOrderId(): String = PG_ORDER_PREFIX + UUID.randomUUID().toString().replace("-", "")
}
