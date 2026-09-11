package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.order.service.OrderService;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.repository.RefundRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundCompletionServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long REFUND_ID = 2L;
    private static final Long PARTICIPATION_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 15, 0);

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock RefundRepository refundRepository;
    @Mock ParticipationService participationService;
    @Mock TimeProvider timeProvider;
    @Mock OrderService orderService;
    private RefundCompletionService refundCompletionService;

    @BeforeEach
    void setUp() {
        refundCompletionService = new RefundCompletionService(
                paymentRepository,
                attemptRepository,
                refundRepository,
                participationService,
                timeProvider,
                orderService
        );
    }

    @Test
    void completedRefundSynchronizesPaymentAndParticipation() {
        Payment payment = paidPayment();
        PaymentAttempt attempt = paidAttempt();
        Refund refund = pendingRefund();
        given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(refundRepository.findByIdForUpdate(REFUND_ID)).willReturn(Optional.of(refund));
        given(attemptRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
                PAYMENT_ID, PaymentAttemptStatus.PAID)).willReturn(Optional.of(attempt));
        given(timeProvider.now()).willReturn(NOW);
        given(participationService.getPaymentInfo(PARTICIPATION_ID))
                .willReturn(new ParticipationPaymentInfo(PARTICIPATION_ID, 1L, 1L, 1, false));

        refundCompletionService.complete(REFUND_ID, new PgCancellationResult(
                "payment-key", "gachisa_order", "cancel-transaction", 12_600));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        verify(participationService).refundPayment(PARTICIPATION_ID);
        verify(orderService).reflectRefund(PAYMENT_ID);
    }

    private Payment paidPayment() {
        Payment payment = Payment.builder()
                .participationId(PARTICIPATION_ID)
                .amount(12_600)
                .status(PaymentStatus.READY)
                .createdAt(NOW.minusMinutes(2))
                .updatedAt(NOW.minusMinutes(2))
                .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.complete(NOW.minusMinutes(1));
        return payment;
    }

    private PaymentAttempt paidAttempt() {
        PaymentAttempt attempt = PaymentAttempt.builder()
                .paymentId(PAYMENT_ID)
                .clientRequestId("768560b7-ec20-4a8d-93fd-c29d003e269f")
                .pgIdempotencyKey("25757835-c3ed-4484-b30f-7f1bea0b1c21")
                .pgOrderId("gachisa_order")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentAttemptStatus.READY)
                .retryCount(0)
                .expiresAt(NOW.plusMinutes(10))
                .createdAt(NOW.minusMinutes(2))
                .updatedAt(NOW.minusMinutes(2))
                .build();
        attempt.beginConfirmation("payment-key", NOW.minusMinutes(1));
        attempt.complete(NOW.minusMinutes(1));
        return attempt;
    }

    private Refund pendingRefund() {
        Refund refund = Refund.builder()
                .paymentId(PAYMENT_ID)
                .amount(12_600)
                .reason("공동구매 목표 인원 미달")
                .status(RefundStatus.REFUND_PENDING)
                .pgIdempotencyKey("4f775a4f-8eea-4f42-a494-8bba7ac3f402")
                .requestedAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1))
                .build();
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }
}
