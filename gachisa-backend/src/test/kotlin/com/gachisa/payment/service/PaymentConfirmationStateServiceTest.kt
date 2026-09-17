package com.gachisa.payment.service

import org.assertj.core.api.Assertions.assertThat
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify

import com.gachisa.global.util.TimeProvider
import com.gachisa.participation.service.ParticipationService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.queue.service.QueueService
import com.gachisa.order.service.OrderService
import com.gachisa.order.dto.OrderCreateCommand
import com.gachisa.order.dto.OrderResponse
import com.gachisa.order.entity.DeliveryStatus
import com.gachisa.payment.client.dto.PgConfirmationResult
import com.gachisa.payment.dto.PaymentConfirmRequest
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class PaymentConfirmationStateServiceTest {

    private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0)

    @Mock lateinit var paymentRepository: PaymentRepository
    @Mock lateinit var attemptRepository: PaymentAttemptRepository
    @Mock lateinit var participationService: ParticipationService
    @Mock lateinit var timeProvider: TimeProvider
    @Mock lateinit var queueService: QueueService
    @Mock lateinit var orderService: OrderService
    private lateinit var stateService: PaymentConfirmationStateService

    @BeforeEach
    fun setUp() {
        stateService = PaymentConfirmationStateService(
                paymentRepository, attemptRepository, participationService, timeProvider,
                queueService, orderService)
    }

    @Test
    fun prepareStoresPaymentKeyOnAttemptBeforeCallingPg() {
        val payment = payment()
        val attempt = attempt()
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L))
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment))
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt))
        given(timeProvider.now()).willReturn(NOW)
        given(participationService.getPaymentInfo(10L))
                .willReturn(ParticipationPaymentInfo(10L, 20L, 30L, 1, true))

        val preparation = stateService.prepare(
                2L, PaymentConfirmRequest("payment-key", "gachisa_order", 12_600))

        assertThat(preparation.requestRequired).isTrue()
        assertThat(payment.status).isEqualTo(PaymentStatus.READY)
        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.PROCESSING)
        assertThat(attempt.pgPaymentKey).isEqualTo("payment-key")
    }

    @Test
    fun duplicateConfirmationWhileProcessingDoesNotCallPgAgain() {
        val payment = payment()
        val attempt = attempt()
        attempt.beginConfirmation("payment-key", NOW)
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L))
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment))
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt))

        val preparation = stateService.prepare(
                2L, PaymentConfirmRequest("payment-key", "gachisa_order", 12_600))

        assertThat(preparation.requestRequired).isFalse()
        assertThat(preparation.existingResponse!!.attemptStatus)
                .isEqualTo(PaymentAttemptStatus.PROCESSING)
    }

    @Test
    fun completedPaymentCreatesOrder() {
        val payment = payment()
        val attempt = attempt()
        attempt.beginConfirmation("payment-key", NOW.minusSeconds(1))
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L))
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment))
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt))
        given(participationService.getPaymentInfo(10L))
                .willReturn(ParticipationPaymentInfo(10L, 20L, 30L, 1, true))
        given(orderService.createOrderIfAbsent(
                OrderCreateCommand(10L, 1L, 20L, 30L, 1, 12_600)))
                .willReturn(orderResponse())
        given(timeProvider.now()).willReturn(NOW)

        val response = stateService.complete(2L, PgConfirmationResult(
                "payment-key", "gachisa_order", 12_600, PaymentMethod.CARD))

        assertThat(response.orderId).isEqualTo(100L)
        verify(orderService).createOrderIfAbsent(
                OrderCreateCommand(10L, 1L, 20L, 30L, 1, 12_600))
    }

    private fun orderResponse(): OrderResponse {
        return OrderResponse(
                100L, "018330029", 10L, 1L, 30L, 40L, "공동구매 상품", null, 1,
                7_875, BigDecimal("0.20"), 3_150, 12_600, false,
                DeliveryStatus.WAITING_FOR_GROUP_BUY, NOW, NOW)
    }

    private fun payment(): Payment {
        val payment = Payment(
                participationId = 10L,
                amount = 12_600,
                status = PaymentStatus.READY,
                createdAt = NOW.minusMinutes(1),
                updatedAt = NOW.minusMinutes(1)
        )
        ReflectionTestUtils.setField(payment, "id", 1L)
        return payment
    }

    private fun attempt(): PaymentAttempt {
        val attempt = PaymentAttempt(
                paymentId = 1L,
                clientRequestId = "768560b7-ec20-4a8d-93fd-c29d003e269f",
                pgIdempotencyKey = "25757835-c3ed-4484-b30f-7f1bea0b1c21",
                pgOrderId = "gachisa_order",
                paymentMethod = PaymentMethod.CARD,
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
