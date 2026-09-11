package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.service.dto.RefundRecoveryTarget;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundRecoveryServiceTest {

    @Mock RefundStateService refundStateService;
    @Mock RefundCompletionService refundCompletionService;
    @Mock RefundService refundService;
    @Mock PgClient pgClient;
    private RefundRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new RefundRecoveryService(
                refundStateService, refundCompletionService, refundService, pgClient);
    }

    @Test
    void cancelledPaymentCompletesPendingRefund() {
        RefundRecoveryTarget target = new RefundRecoveryTarget(1L, "payment-key", "order-id", 12_600);
        PgPaymentQueryResult queryResult = new PgPaymentQueryResult(
                "payment-key", "order-id", 12_600, "CANCELED", PaymentMethod.CARD,
                "cancel-transaction", "목표 미달", 12_600);
        given(refundStateService.getRecoveryTarget(1L)).willReturn(target);
        given(pgClient.getPayment("payment-key")).willReturn(queryResult);
        given(refundCompletionService.complete(1L, new PgCancellationResult(
                "payment-key", "order-id", "cancel-transaction", 12_600)))
                .willReturn(refundResponse(RefundStatus.REFUNDED));

        RefundResponse response = recoveryService.recover(1L);

        assertThat(response.status()).isEqualTo(RefundStatus.REFUNDED);
    }

    @Test
    void paymentStillDoneRetriesCancellationWithSameRefund() {
        RefundRecoveryTarget target = new RefundRecoveryTarget(1L, "payment-key", "order-id", 12_600);
        PgPaymentQueryResult queryResult = new PgPaymentQueryResult(
                "payment-key", "order-id", 12_600, "DONE", PaymentMethod.CARD,
                null, null, 0);
        given(refundStateService.getRecoveryTarget(1L)).willReturn(target);
        given(pgClient.getPayment("payment-key")).willReturn(queryResult);
        given(refundService.processPending(1L)).willReturn(refundResponse(RefundStatus.REFUNDED));

        recoveryService.recover(1L);

        verify(refundStateService).retryPending(1L);
        verify(refundService).processPending(1L);
    }

    private RefundResponse refundResponse(RefundStatus status) {
        return new RefundResponse(
                1L, 10L, 12_600, "목표 미달", status,
                0, null,
                null, null, LocalDateTime.of(2026, 8, 18, 12, 0), null
        );
    }
}
