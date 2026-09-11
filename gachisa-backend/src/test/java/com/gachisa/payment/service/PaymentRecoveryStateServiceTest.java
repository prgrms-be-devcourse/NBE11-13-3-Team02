package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.order.service.OrderService;
import com.gachisa.order.dto.OrderCreateCommand;
import com.gachisa.order.dto.OrderResponse;
import com.gachisa.order.entity.DeliveryStatus;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryStateServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 12, 0);
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock ParticipationService participationService;
    @Mock RefundCompletionService refundCompletionService;
    @Mock TimeProvider timeProvider;
    @Mock OrderService orderService;
    private final PgRetryPolicy retryPolicy = new PgRetryPolicy();
    private PaymentRecoveryStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new PaymentRecoveryStateService(
                paymentRepository, attemptRepository, participationService, refundCompletionService,
                timeProvider, retryPolicy, orderService);
    }

    @Test
    void doneStatusRecoversPaymentAndParticipation() {
        Payment payment = payment();
        PaymentAttempt attempt = processingAttempt();
        mockLocked(payment, attempt);
        given(timeProvider.now()).willReturn(NOW);
        given(participationService.getPaymentInfo(10L))
                .willReturn(new ParticipationPaymentInfo(10L, 20L, 30L, 1, false));
        given(orderService.createOrderIfAbsent(
                new OrderCreateCommand(10L, 1L, 20L, 30L, 1, 12_600)))
                .willReturn(new OrderResponse(
                        100L, "018330029", 10L, 1L, 30L, 40L, "공동구매 상품", null, 1,
                        7_875, new BigDecimal("0.20"), 3_150, 12_600, false,
                        DeliveryStatus.WAITING_FOR_GROUP_BUY, NOW, NOW));

        var response = stateService.apply(2L, result("DONE"));

        assertThat(response.orderId()).isEqualTo(100L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PAID);
        verify(participationService).confirmPayment(10L);
        verify(orderService).createOrderIfAbsent(
                new OrderCreateCommand(10L, 1L, 20L, 30L, 1, 12_600));
    }

    @Test
    void abortedStatusFailsOnlyAttemptSoAnotherAttemptCanBeCreated() {
        Payment payment = payment();
        PaymentAttempt attempt = processingAttempt();
        mockLocked(payment, attempt);
        given(timeProvider.now()).willReturn(NOW);

        stateService.apply(2L, result("ABORTED"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getFailureCode()).isEqualTo("TOSS_PAYMENT_ABORTED");
    }

    @Test
    void expiredStatusExpiresOnlyAttempt() {
        Payment payment = payment();
        PaymentAttempt attempt = processingAttempt();
        mockLocked(payment, attempt);
        given(timeProvider.now()).willReturn(NOW);

        stateService.apply(2L, result("EXPIRED"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
    }

    private void mockLocked(Payment payment, PaymentAttempt attempt) {
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L));
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt));
    }

    private Payment payment() {
        Payment payment = Payment.builder().participationId(10L).amount(12_600)
                .status(PaymentStatus.READY).createdAt(NOW.minusMinutes(2))
                .updatedAt(NOW.minusMinutes(2)).build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        return payment;
    }

    private PaymentAttempt processingAttempt() {
        PaymentAttempt attempt = PaymentAttempt.builder().paymentId(1L)
                .clientRequestId("768560b7-ec20-4a8d-93fd-c29d003e269f")
                .pgIdempotencyKey("25757835-c3ed-4484-b30f-7f1bea0b1c21")
                .pgOrderId("gachisa_order").paymentMethod(PaymentMethod.CARD)
                .status(PaymentAttemptStatus.READY).retryCount(0)
                .expiresAt(NOW.plusMinutes(10)).createdAt(NOW.minusMinutes(2))
                .updatedAt(NOW.minusMinutes(2)).build();
        ReflectionTestUtils.setField(attempt, "id", 2L);
        attempt.beginConfirmation("payment-key", NOW.minusMinutes(1));
        return attempt;
    }

    private PgPaymentQueryResult result(String status) {
        return new PgPaymentQueryResult("payment-key", "gachisa_order", 12_600, status,
                PaymentMethod.CARD, null, null, 0);
    }
}
