package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.order.service.OrderService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgConfirmationResult;
import com.gachisa.payment.dto.PaymentConfirmRequest;
import com.gachisa.payment.dto.PaymentRequest;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.service.dto.ConfirmationPreparation;
import com.gachisa.queue.service.QueueService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long PARTICIPATION_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String CLIENT_KEY = "768560b7-ec20-4a8d-93fd-c29d003e269f";
    private static final String QUEUE_TOKEN = "queue-token";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 12, 0);
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock ParticipationService participationService;
    @Mock PaymentAmountCalculator amountCalculator;
    @Mock PgClient pgClient;
    @Mock PaymentConfirmationStateService confirmationStateService;
    @Mock TimeProvider timeProvider;
    @Mock QueueService queueService;
    @Mock OrderService orderService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, attemptRepository,
                participationService, amountCalculator, pgClient,
                confirmationStateService, timeProvider, queueService, orderService);
    }

    @Test
    void createPaymentCreatesParentAndFirstAttempt() {
        mockPaymentInfo();
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.empty());
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.empty());
        given(timeProvider.now()).willReturn(NOW);
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID))
                .willReturn(Optional.of(payment()));
        given(attemptRepository.save(any(PaymentAttempt.class))).willAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", 2L);
            return attempt;
        });

        PaymentResponse response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, new PaymentRequest(PaymentMethod.CARD));

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(response.attemptStatus()).isEqualTo(PaymentAttemptStatus.READY);
        assertThat(response.amount()).isEqualTo(12_600);
        assertThat(response.paymentAttemptId()).isEqualTo(2L);
    }

    @Test
    void concurrentSameClientKeyReturnsAttemptCreatedWhileWaitingForPaymentLock() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        AtomicInteger lookupCount = new AtomicInteger();
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(attemptRepository.findByClientRequestId(CLIENT_KEY))
                .willAnswer(invocation -> lookupCount.getAndIncrement() == 0
                        ? Optional.empty()
                        : Optional.of(attempt));
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.of(payment));
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID))
                .willReturn(Optional.of(payment));
        given(timeProvider.now()).willReturn(NOW);

        PaymentResponse response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, new PaymentRequest(PaymentMethod.CARD));

        assertThat(response.paymentAttemptId()).isEqualTo(2L);
    }

    @Test
    void sameClientKeyReturnsSameAttempt() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.of(attempt));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        PaymentResponse response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, new PaymentRequest(PaymentMethod.CARD));

        assertThat(response.paymentAttemptId()).isEqualTo(2L);
    }

    @Test
    void sameClientKeyWithDifferentPayloadReturnsUnprocessableEntity() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.of(attempt));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.createPayment(
                PARTICIPATION_ID,
                USER_ID,
                CLIENT_KEY,
                QUEUE_TOKEN,
                new PaymentRequest(PaymentMethod.EASY_PAY)
        ))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    ErrorCode errorCode = ((CustomException) exception).getErrorCode();
                    assertThat(errorCode).isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT);
                    assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                });
    }

    @Test
    void changingMethodCancelsReadyAttemptAndCreatesNewAttempt() {
        Payment payment = payment();
        PaymentAttempt oldAttempt = attempt(PaymentMethod.CARD);
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(attemptRepository.findByClientRequestId(CLIENT_KEY)).willReturn(Optional.empty());
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.of(payment));
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID))
                .willReturn(Optional.of(payment));
        given(attemptRepository.findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .willReturn(Optional.of(oldAttempt));
        given(timeProvider.now()).willReturn(NOW);
        given(attemptRepository.save(any(PaymentAttempt.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, CLIENT_KEY, QUEUE_TOKEN, new PaymentRequest(PaymentMethod.EASY_PAY));

        assertThat(oldAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.CANCELLED);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.EASY_PAY);
    }

    @Test
    void confirmUsesAttemptPgIdempotencyKey() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "gachisa_order", 12_600);
        given(attemptRepository.findById(2L)).willReturn(Optional.of(attempt));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        var preparation = new ConfirmationPreparation(2L, "payment-key", "gachisa_order",
                12_600, "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD, true, null);
        given(confirmationStateService.prepare(2L, request)).willReturn(preparation);
        PgConfirmationResult result = new PgConfirmationResult(
                "payment-key", "gachisa_order", 12_600, PaymentMethod.CARD);
        given(pgClient.confirm("payment-key", "gachisa_order", 12_600,
                "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD)).willReturn(result);
        given(confirmationStateService.complete(2L, result)).willReturn(PaymentResponse.from(payment, attempt));

        paymentService.confirmPayment(2L, USER_ID, request);

        verify(confirmationStateService).complete(2L, result);
    }

    @Test
    void invalidClientKeyIsRejected() {
        assertThatThrownBy(() -> paymentService.createPayment(
                PARTICIPATION_ID, USER_ID, "not-uuid", QUEUE_TOKEN, new PaymentRequest(PaymentMethod.CARD)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_INVALID);
    }

    @Test
    void temporaryPgFailureKeepsAttemptForRecovery() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "gachisa_order", 12_600);
        given(attemptRepository.findById(2L)).willReturn(Optional.of(attempt));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        var preparation = new ConfirmationPreparation(2L, "payment-key", "gachisa_order",
                12_600, "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD, true, null);
        given(confirmationStateService.prepare(2L, request)).willReturn(preparation);
        CustomException temporaryFailure = new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        given(pgClient.confirm("payment-key", "gachisa_order", 12_600,
                "25757835-c3ed-4484-b30f-7f1bea0b1c21", PaymentMethod.CARD))
                .willThrow(temporaryFailure);

        assertThatThrownBy(() -> paymentService.confirmPayment(2L, USER_ID, request))
                .isSameAs(temporaryFailure);

        verify(confirmationStateService, never()).fail(2L, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    @Test
    void paymentCanBeRecoveredByPgOrderIdWithoutBrowserStorage() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        given(attemptRepository.findByPgOrderId("gachisa_order")).willReturn(Optional.of(attempt));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());

        PaymentResponse response = paymentService.getPaymentByPgOrderId("gachisa_order", USER_ID);

        assertThat(response.paymentAttemptId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualTo(12_600);
    }

    @Test
    void anotherUserCannotRecoverPaymentByPgOrderId() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(PaymentMethod.CARD);
        given(attemptRepository.findByPgOrderId("gachisa_order")).willReturn(Optional.of(attempt));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());

        assertThatThrownBy(() -> paymentService.getPaymentByPgOrderId("gachisa_order", 999L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void unknownPgOrderIdCannotBeConfirmed() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "unknown-order", 12_600);
        given(attemptRepository.findByPgOrderId("unknown-order")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPaymentByPgOrderId(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND);
    }

    private void mockPaymentInfo() {
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(amountCalculator.calculate(paymentInfo())).willReturn(12_600);
    }

    private ParticipationPaymentInfo paymentInfo() {
        return new ParticipationPaymentInfo(PARTICIPATION_ID, USER_ID, 1L, 1, true);
    }

    private Payment payment() {
        Payment payment = Payment.builder().participationId(PARTICIPATION_ID).amount(12_600)
                .status(PaymentStatus.READY).createdAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1)).build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        return payment;
    }

    private PaymentAttempt attempt(PaymentMethod method) {
        PaymentAttempt attempt = PaymentAttempt.builder().paymentId(1L).clientRequestId(CLIENT_KEY)
                .pgIdempotencyKey("25757835-c3ed-4484-b30f-7f1bea0b1c21")
                .pgOrderId("gachisa_order").paymentMethod(method)
                .status(PaymentAttemptStatus.READY).retryCount(0)
                .expiresAt(NOW.plusMinutes(10)).createdAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1)).build();
        ReflectionTestUtils.setField(attempt, "id", 2L);
        return attempt;
    }
}
