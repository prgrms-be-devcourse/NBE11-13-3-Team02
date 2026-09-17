package com.gachisa.payment.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.never

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.order.service.OrderService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgConfirmationResult
import com.gachisa.payment.dto.PaymentConfirmRequest
import com.gachisa.payment.dto.PaymentRequest
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.metric.PaymentMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.gachisa.payment.service.dto.ConfirmationPreparation
import com.gachisa.queue.service.QueueService
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class PaymentServiceTest {

    private val PARTICIPATION_ID: Long = 1L
    private val USER_ID: Long = 10L
    private val CLIENT_KEY: String = "768560b7-ec20-4a8d-93fd-c29d003e269f"
    private val QUEUE_TOKEN: String = "queue-token"
    private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0)
    @Mock lateinit var paymentRepository: PaymentRepository
    @Mock lateinit var attemptRepository: PaymentAttemptRepository
    @Mock lateinit var participationService: ParticipationService
    @Mock lateinit var amountCalculator: PaymentAmountCalculator
    @Mock lateinit var pgClient: PgClient
    @Mock lateinit var confirmationStateService: PaymentConfirmationStateService
    @Mock lateinit var timeProvider: TimeProvider
    @Mock lateinit var queueService: QueueService
    @Mock lateinit var orderService: OrderService
    private lateinit var paymentMetrics: PaymentMetrics
    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentMetrics = PaymentMetrics(SimpleMeterRegistry())
        paymentService = PaymentService(paymentRepository, attemptRepository,
                participationService, amountCalculator, pgClient,
                confirmationStateService, timeProvider, queueService, orderService, paymentMetrics)
    }

    @Test
    fun createPaymentCreatesParentAndFirstAttempt() {
        mockPaymentInfo()
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.empty())
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.empty())
        given(timeProvider.now()).willReturn(NOW)
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID))
                .willReturn(Optional.of(payment()))
        given(attemptRepository.save(any(PaymentAttempt::class.java))).willAnswer { invocation ->
            val attempt = invocation.getArgument<PaymentAttempt>(0)
            ReflectionTestUtils.setField(attempt, "id", 2L)
            attempt
        }

        val response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, PaymentRequest(PaymentMethod.CARD))

        assertThat(response.paymentStatus).isEqualTo(PaymentStatus.READY)
        assertThat(response.attemptStatus).isEqualTo(PaymentAttemptStatus.READY)
        assertThat(response.amount).isEqualTo(12_600)
        assertThat(response.paymentAttemptId).isEqualTo(2L)
    }

    @Test
    fun concurrentSameClientKeyReturnsAttemptCreatedWhileWaitingForPaymentLock() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        val lookupCount = AtomicInteger()
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        given(attemptRepository.findByClientRequestId(CLIENT_KEY))
                .willAnswer { if (lookupCount.getAndIncrement() == 0) Optional.empty() else Optional.of(attempt) }
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.of(payment))
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID))
                .willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(NOW)

        val response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, PaymentRequest(PaymentMethod.CARD))

        assertThat(response.paymentAttemptId).isEqualTo(2L)
    }

    @Test
    fun sameClientKeyReturnsSameAttempt() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.of(attempt))
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment))

        val response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, PaymentRequest(PaymentMethod.CARD))

        assertThat(response.paymentAttemptId).isEqualTo(2L)
    }

    @Test
    fun sameClientKeyWithDifferentPayloadReturnsUnprocessableEntity() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.of(attempt))
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment))

        assertThatThrownBy { paymentService.createPayment(
                PARTICIPATION_ID,
                USER_ID,
                CLIENT_KEY,
                QUEUE_TOKEN,
                PaymentRequest(PaymentMethod.EASY_PAY)
        ) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT)
        assertThat(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT.status)
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
    }

    @Test
    fun changingMethodCancelsReadyAttemptAndCreatesNewAttempt() {
        val payment = payment()
        val oldAttempt = attempt(PaymentMethod.CARD)
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.empty())
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.of(payment))
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID))
                .willReturn(Optional.of(payment))
        given(attemptRepository.findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
                1L, listOf(PaymentAttemptStatus.READY, PaymentAttemptStatus.PROCESSING)))
                .willReturn(Optional.of(oldAttempt))
        given(timeProvider.now()).willReturn(NOW)
        given(attemptRepository.save(any(PaymentAttempt::class.java))).willAnswer { invocation ->
            val savedAttempt = invocation.getArgument<PaymentAttempt>(0)
            ReflectionTestUtils.setField(savedAttempt, "id", 3L)
            savedAttempt
        }

        val response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, PaymentRequest(PaymentMethod.EASY_PAY))

        assertThat(oldAttempt.status).isEqualTo(PaymentAttemptStatus.CANCELLED)
        assertThat(response.paymentMethod).isEqualTo(PaymentMethod.EASY_PAY)
    }

    @Test
    fun confirmUsesAttemptPgIdempotencyKey() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        val request = PaymentConfirmRequest("payment-key", "gachisa_order", 12_600)
        given(attemptRepository.findById(2L)).willReturn(Optional.of(attempt))
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment))
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        val preparation = ConfirmationPreparation(2L, "payment-key", "gachisa_order",
                12_600, "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD, true, null)
        given(confirmationStateService.prepare(2L, request)).willReturn(preparation)
        val result = PgConfirmationResult(
                "payment-key", "gachisa_order", 12_600, PaymentMethod.CARD)
        given(pgClient.confirm("payment-key", "gachisa_order", 12_600,
                "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD)).willReturn(result)
        given(confirmationStateService.complete(2L, result)).willReturn(PaymentResponse.from(payment, attempt))

        paymentService.confirmPayment(2L, USER_ID, request)

        verify(confirmationStateService).complete(2L, result)
    }

    @Test
    fun invalidClientKeyIsRejected() {
        assertThatThrownBy { paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, "not-uuid", QUEUE_TOKEN, PaymentRequest(PaymentMethod.CARD)) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_INVALID)
    }

    @Test
    fun temporaryPgFailureKeepsAttemptForRecovery() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        val request = PaymentConfirmRequest("payment-key", "gachisa_order", 12_600)
        given(attemptRepository.findById(2L)).willReturn(Optional.of(attempt))
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment))
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        val preparation = ConfirmationPreparation(2L, "payment-key", "gachisa_order",
                12_600, "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD, true, null)
        given(confirmationStateService.prepare(2L, request)).willReturn(preparation)
        val temporaryFailure = CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        given(pgClient.confirm("payment-key", "gachisa_order", 12_600,
                "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD))
                .willThrow(temporaryFailure)

        assertThatThrownBy { paymentService.confirmPayment(2L, USER_ID, request) }
                .isSameAs(temporaryFailure)

        verify(confirmationStateService, never()).fail(2L, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
    }

    @Test
    fun paymentCanBeRecoveredByPgOrderIdWithoutBrowserStorage() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        given(attemptRepository.findByPgOrderId("gachisa_order")).willReturn(Optional.of(attempt))
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment))
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())

        val response = paymentService.getPaymentByPgOrderId("gachisa_order", USER_ID)

        assertThat(response.paymentAttemptId).isEqualTo(2L)
        assertThat(response.amount).isEqualTo(12_600)
    }

    @Test
    fun anotherUserCannotRecoverPaymentByPgOrderId() {
        val payment = payment()
        val attempt = attempt(PaymentMethod.CARD)
        given(attemptRepository.findByPgOrderId("gachisa_order")).willReturn(Optional.of(attempt))
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment))
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())

        assertThatThrownBy { paymentService.getPaymentByPgOrderId("gachisa_order", 999L) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.FORBIDDEN)
    }

    @Test
    fun unknownPgOrderIdCannotBeConfirmed() {
        val request = PaymentConfirmRequest("payment-key", "unknown-order", 12_600)
        given(attemptRepository.findByPgOrderId("unknown-order")).willReturn(Optional.empty())

        assertThatThrownBy { paymentService.confirmPaymentByPgOrderId(USER_ID, request) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND)
    }

    private fun mockPaymentInfo() {
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo())
        given(amountCalculator.calculate(paymentInfo())).willReturn(12_600)
    }

    private fun paymentInfo(): ParticipationPaymentInfo {
        return ParticipationPaymentInfo(PARTICIPATION_ID, USER_ID, 1L, 1, true)
    }

    private fun payment(): Payment {
        val payment = Payment(
                participationId = PARTICIPATION_ID,
                amount = 12_600,
                status = PaymentStatus.READY,
                createdAt = NOW.minusMinutes(1),
                updatedAt = NOW.minusMinutes(1)
        )
        ReflectionTestUtils.setField(payment, "id", 1L)
        return payment
    }

    private fun attempt(method: PaymentMethod): PaymentAttempt {
        val attempt = PaymentAttempt(
                paymentId = 1L,
                clientRequestId = CLIENT_KEY,
                pgIdempotencyKey = "25757835-c3ed-4484-b30f-7f1bea0b1c21",
                pgOrderId = "gachisa_order",
                paymentMethod = method,
                status = PaymentAttemptStatus.READY,
                retryCount = 0,
                expiresAt = NOW.plusMinutes(10),
                createdAt = NOW.minusMinutes(1),
                updatedAt = NOW.minusMinutes(1)
        )
        ReflectionTestUtils.setField(attempt, "id", 2L)
        return attempt
    }
}
