package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.queue.service.QueueService;
import com.gachisa.order.service.OrderService;
import com.gachisa.order.dto.OrderCreateCommand;
import com.gachisa.order.dto.OrderResponse;
import com.gachisa.order.entity.DeliveryStatus;
import com.gachisa.payment.client.dto.PgConfirmationResult;
import com.gachisa.payment.dto.PaymentConfirmRequest;
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
class PaymentConfirmationStateServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 12, 0);

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock ParticipationService participationService;
    @Mock TimeProvider timeProvider;
    @Mock QueueService queueService;
    @Mock OrderService orderService;
    private PaymentConfirmationStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new PaymentConfirmationStateService(
                paymentRepository, attemptRepository, participationService, timeProvider,
                queueService, orderService);
    }

    @Test
    void prepareStoresPaymentKeyOnAttemptBeforeCallingPg() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt();
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L));
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt));
        given(timeProvider.now()).willReturn(NOW);
        given(participationService.getPaymentInfo(10L))
                .willReturn(new ParticipationPaymentInfo(10L, 20L, 30L, 1, true));

        var preparation = stateService.prepare(
                2L, new PaymentConfirmRequest("payment-key", "gachisa_order", 12_600));

        assertThat(preparation.requestRequired()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(attempt.getPgPaymentKey()).isEqualTo("payment-key");
    }

    @Test
    void duplicateConfirmationWhileProcessingDoesNotCallPgAgain() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt();
        attempt.beginConfirmation("payment-key", NOW);
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L));
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt));

        var preparation = stateService.prepare(
                2L, new PaymentConfirmRequest("payment-key", "gachisa_order", 12_600));

        assertThat(preparation.requestRequired()).isFalse();
        assertThat(preparation.existingResponse().attemptStatus())
                .isEqualTo(PaymentAttemptStatus.PROCESSING);
    }

    @Test
    void completedPaymentCreatesOrder() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt();
        attempt.beginConfirmation("payment-key", NOW.minusSeconds(1));
        given(attemptRepository.findPaymentIdByAttemptId(2L)).willReturn(Optional.of(1L));
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(attemptRepository.findByIdForUpdate(2L)).willReturn(Optional.of(attempt));
        given(participationService.getPaymentInfo(10L))
                .willReturn(new ParticipationPaymentInfo(10L, 20L, 30L, 1, true));
        given(orderService.createOrderIfAbsent(
                new OrderCreateCommand(10L, 1L, 20L, 30L, 1, 12_600)))
                .willReturn(orderResponse());
        given(timeProvider.now()).willReturn(NOW);

        var response = stateService.complete(2L, new PgConfirmationResult(
                "payment-key", "gachisa_order", 12_600, PaymentMethod.CARD));

        assertThat(response.orderId()).isEqualTo(100L);
        verify(orderService).createOrderIfAbsent(
                new OrderCreateCommand(10L, 1L, 20L, 30L, 1, 12_600));
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(
                100L, "018330029", 10L, 1L, 30L, 40L, "공동구매 상품", null, 1,
                7_875, new BigDecimal("0.20"), 3_150, 12_600, false,
                DeliveryStatus.WAITING_FOR_GROUP_BUY, NOW, NOW);
    }

    private Payment payment() {
        Payment payment = Payment.builder().participationId(10L).amount(12_600)
                .status(PaymentStatus.READY).createdAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1)).build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        return payment;
    }

    private PaymentAttempt attempt() {
        PaymentAttempt attempt = PaymentAttempt.builder().paymentId(1L)
                .clientRequestId("768560b7-ec20-4a8d-93fd-c29d003e269f")
                .pgIdempotencyKey("25757835-c3ed-4484-b30f-7f1bea0b1c21")
                .pgOrderId("gachisa_order").paymentMethod(PaymentMethod.CARD)
                .status(PaymentAttemptStatus.READY).retryCount(0)
                .expiresAt(NOW.plusMinutes(10)).createdAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1)).build();
        ReflectionTestUtils.setField(attempt, "id", 2L);
        return attempt;
    }
}
