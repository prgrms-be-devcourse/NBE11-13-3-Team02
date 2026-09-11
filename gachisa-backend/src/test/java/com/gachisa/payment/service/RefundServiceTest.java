package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.service.dto.RefundPreparation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long REFUND_ID = 2L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String REASON = "공동구매 목표 인원 미달";
    private static final String IDEMPOTENCY_KEY = "4f775a4f-8eea-4f42-a494-8bba7ac3f402";

    @Mock
    private RefundStateService refundStateService;

    @Mock
    private RefundCompletionService refundCompletionService;

    @Mock
    private PgClient pgClient;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(refundStateService, refundCompletionService, pgClient);
    }

    @Test
    void refundCancelsPgPaymentAndCompletesRefund() {
        RefundPreparation preparation = new RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, true);
        PgCancellationResult cancellation = new PgCancellationResult(
                PAYMENT_KEY, "gachisa_order", "cancel-transaction", 12_600);
        RefundResponse completed = refundedResponse();

        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation);
        given(pgClient.cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)).willReturn(cancellation);
        given(refundCompletionService.complete(REFUND_ID, cancellation)).willReturn(completed);

        RefundResponse response = refundService.processPending(REFUND_ID);

        assertThat(response.status()).isEqualTo(RefundStatus.REFUNDED);
        verify(pgClient).cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY);
    }

    @Test
    void refundReturnsExistingResultWithoutCallingPgAgain() {
        RefundPreparation preparation = new RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, false);
        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation);
        given(refundStateService.getRefund(REFUND_ID)).willReturn(refundedResponse());

        RefundResponse response = refundService.processPending(REFUND_ID);

        assertThat(response.status()).isEqualTo(RefundStatus.REFUNDED);
        verify(pgClient, never()).cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY);
    }

    @Test
    void temporaryPgFailureKeepsRefundPending() {
        RefundPreparation preparation = new RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, true);
        CustomException pgFailure = new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation);
        given(pgClient.cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)).willThrow(pgFailure);

        assertThatThrownBy(() -> refundService.processPending(REFUND_ID))
                .isSameAs(pgFailure);
        verify(refundStateService).keepPending(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        verify(refundStateService, never()).fail(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    @Test
    void definitivePgRejectionFailsRefund() {
        RefundPreparation preparation = new RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, true);
        CustomException pgFailure = new CustomException(ErrorCode.PAYMENT_GATEWAY_REJECTED);
        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation);
        given(pgClient.cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)).willThrow(pgFailure);

        assertThatThrownBy(() -> refundService.processPending(REFUND_ID))
                .isSameAs(pgFailure);
        verify(refundStateService).fail(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_REJECTED);
        verify(refundStateService, never()).keepPending(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_REJECTED);
    }

    private RefundResponse refundedResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);
        return new RefundResponse(
                REFUND_ID,
                PAYMENT_ID,
                12_600,
                REASON,
                RefundStatus.REFUNDED,
                0,
                null,
                null,
                null,
                now.minusMinutes(1),
                now
        );
    }
}
