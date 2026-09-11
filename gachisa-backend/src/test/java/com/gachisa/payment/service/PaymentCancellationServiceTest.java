package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.payment.dto.PaymentCancellationResponse;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.RefundRepository;
import com.gachisa.queue.service.QueueService;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationServiceTest {

    private static final Long PARTICIPATION_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 12, 0);

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository paymentAttemptRepository;
    @Mock RefundRepository refundRepository;
    @Mock ParticipationService participationService;
    @Mock RefundService refundService;
    @Mock QueueService queueService;
    @Mock TimeProvider timeProvider;
    private PaymentCancellationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCancellationService(
                paymentRepository, paymentAttemptRepository, refundRepository,
                participationService, refundService, queueService, timeProvider);
    }

    @Test
    void cancellingBeforePgApprovalReleasesParticipationAndQueue() {
        Payment payment = payment(PaymentStatus.READY);
        PaymentAttempt attempt = PaymentAttempt.builder()
                .paymentId(payment.getId())
                .clientRequestId("768560b7-ec20-4a8d-93fd-c29d003e269f")
                .pgIdempotencyKey("25757835-c3ed-4484-b30f-7f1bea0b1c21")
                .pgOrderId("gachisa_order")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentAttemptStatus.READY)
                .retryCount(0)
                .expiresAt(NOW.plusMinutes(10))
                .createdAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1))
                .build();
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID)).willReturn(Optional.of(payment));
        given(paymentAttemptRepository.findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
                payment.getId(), List.of(PaymentAttemptStatus.READY, PaymentAttemptStatus.PROCESSING)))
                .willReturn(Optional.of(attempt));
        given(timeProvider.now()).willReturn(NOW);

        PaymentCancellationResponse response = service.cancel(PARTICIPATION_ID, USER_ID);

        assertThat(response.result()).isEqualTo("PARTICIPATION_CANCELLED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CANCELLED);
        verify(participationService).cancel(PARTICIPATION_ID, USER_ID);
        verify(queueService).completeAdmission(1L, USER_ID);
    }

    @Test
    void paidParticipationReturnsRefundProgress() {
        Payment payment = payment(PaymentStatus.PAID);
        RefundResponse refund = refundResponse(RefundStatus.REFUND_PENDING);
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(paymentRepository.findByParticipationIdForUpdate(PARTICIPATION_ID)).willReturn(Optional.of(payment));
        given(refundService.requestRefund(payment.getId(), "구매자 공동구매 참여 취소"))
                .willReturn(refund);

        PaymentCancellationResponse response = service.cancel(PARTICIPATION_ID, USER_ID);

        assertThat(response.result()).isEqualTo("REFUND_REQUESTED");
        assertThat(response.refund().status()).isEqualTo(RefundStatus.REFUND_PENDING);
    }

    @Test
    void anotherUserCannotReadRefundStatus() {
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());

        assertThatThrownBy(() -> service.getRefundStatus(PARTICIPATION_ID, 999L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void missingRefundReturnsNotFound() {
        Payment payment = payment(PaymentStatus.PAID);
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.of(payment));
        given(refundRepository.findByPaymentId(payment.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRefundStatus(PARTICIPATION_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_FOUND);
    }

    @Test
    void completedRefundCanBeRead() {
        Payment payment = payment(PaymentStatus.REFUNDED);
        Refund refund = Refund.builder()
                .paymentId(payment.getId())
                .amount(12_600)
                .reason("구매자 공동구매 참여 취소")
                .status(RefundStatus.REFUND_PENDING)
                .pgIdempotencyKey("4f775a4f-8eea-4f42-a494-8bba7ac3f402")
                .requestedAt(NOW.minusMinutes(1))
                .updatedAt(NOW.minusMinutes(1))
                .build();
        refund.complete("cancel-transaction", NOW);
        ReflectionTestUtils.setField(refund, "id", 3L);
        given(participationService.getPaymentInfo(PARTICIPATION_ID)).willReturn(paymentInfo());
        given(paymentRepository.findByParticipationId(PARTICIPATION_ID)).willReturn(Optional.of(payment));
        given(refundRepository.findByPaymentId(payment.getId())).willReturn(Optional.of(refund));

        RefundResponse response = service.getRefundStatus(PARTICIPATION_ID, USER_ID);

        assertThat(response.status()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(response.refundedAt()).isEqualTo(NOW);
    }

    private ParticipationPaymentInfo paymentInfo() {
        return new ParticipationPaymentInfo(PARTICIPATION_ID, USER_ID, 1L, 1, true);
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .participationId(PARTICIPATION_ID)
                .amount(12_600)
                .status(status)
                .createdAt(NOW.minusMinutes(2))
                .updatedAt(NOW.minusMinutes(2))
                .build();
        ReflectionTestUtils.setField(payment, "id", 2L);
        return payment;
    }

    private RefundResponse refundResponse(RefundStatus status) {
        return new RefundResponse(
                3L, 2L, 12_600, "구매자 공동구매 참여 취소", status,
                0, NOW, null, null, NOW, null);
    }
}
