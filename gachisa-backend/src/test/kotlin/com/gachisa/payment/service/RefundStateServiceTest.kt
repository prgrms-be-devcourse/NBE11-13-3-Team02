package com.gachisa.payment.service

import org.assertj.core.api.Assertions.assertThat
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify

import com.gachisa.global.util.TimeProvider
import com.gachisa.order.service.OrderService
import com.gachisa.participation.service.ParticipationService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.entity.Refund
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.repository.RefundRepository
import java.time.LocalDateTime
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class RefundStateServiceTest {

    private val PAYMENT_ID: Long = 1L
    private val REFUND_ID: Long = 2L
    private val PARTICIPATION_ID: Long = 3L
    private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 14, 15, 0)

    @Mock lateinit var paymentRepository: PaymentRepository
    @Mock lateinit var attemptRepository: PaymentAttemptRepository
    @Mock lateinit var refundRepository: RefundRepository
    @Mock lateinit var participationService: ParticipationService
    @Mock lateinit var timeProvider: TimeProvider
    @Mock lateinit var orderService: OrderService
    private lateinit var refundCompletionService: RefundCompletionService

    @BeforeEach
    fun setUp() {
        refundCompletionService = RefundCompletionService(
                paymentRepository,
                attemptRepository,
                refundRepository,
                participationService,
                timeProvider,
                orderService
        )
    }

    @Test
    fun completedRefundSynchronizesPaymentAndParticipation() {
        val payment = paidPayment()
        val attempt = paidAttempt()
        val refund = pendingRefund()
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund))
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment))
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.of(refund))
        given(attemptRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
                PAYMENT_ID, PaymentAttemptStatus.PAID)).willReturn(Optional.of(attempt))
        given(timeProvider.now()).willReturn(NOW)
        given(participationService.getPaymentInfo(PARTICIPATION_ID))
                .willReturn(ParticipationPaymentInfo(PARTICIPATION_ID, 1L, 1L, 1, false))

        refundCompletionService.complete(REFUND_ID, PgCancellationResult(
                "payment-key", "gachisa_order", "cancel-transaction", 12_600))

        assertThat(payment.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(refund.status).isEqualTo(RefundStatus.REFUNDED)
        verify(participationService).refundPayment(PARTICIPATION_ID)
        verify(orderService).reflectRefund(PAYMENT_ID)
    }

    private fun paidPayment(): Payment {
        val payment = Payment(
                participationId = PARTICIPATION_ID,
                amount = 12_600,
                status = PaymentStatus.READY,
                createdAt = NOW.minusMinutes(2),
                updatedAt = NOW.minusMinutes(2)
        )
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID)
        payment.complete(NOW.minusMinutes(1))
        return payment
    }

    private fun paidAttempt(): PaymentAttempt {
        val attempt = PaymentAttempt(
                paymentId = PAYMENT_ID,
                clientRequestId = "768560b7-ec20-4a8d-93fd-c29d003e269f",
                pgIdempotencyKey = "25757835-c3ed-4484-b30f-7f1bea0b1c21",
                pgOrderId = "gachisa_order",
                paymentMethod = PaymentMethod.CARD,
                status = PaymentAttemptStatus.READY,
                retryCount = 0,
                expiresAt = NOW.plusMinutes(10),
                createdAt = NOW.minusMinutes(2),
                updatedAt = NOW.minusMinutes(2)
        )
        attempt.beginConfirmation("payment-key", NOW.minusMinutes(1))
        attempt.complete(NOW.minusMinutes(1))
        return attempt
    }

    private fun pendingRefund(): Refund {
        val refund = Refund(
                paymentId = PAYMENT_ID,
                amount = 12_600,
                reason = "공동구매 목표 인원 미달",
                status = RefundStatus.REFUND_PENDING,
                pgIdempotencyKey = "4f775a4f-8eea-4f42-a494-8bba7ac3f402",
                requestedAt = NOW.minusMinutes(1),
                updatedAt = NOW.minusMinutes(1)
        )
        ReflectionTestUtils.setField(refund, "id", REFUND_ID)
        return refund
    }
}
