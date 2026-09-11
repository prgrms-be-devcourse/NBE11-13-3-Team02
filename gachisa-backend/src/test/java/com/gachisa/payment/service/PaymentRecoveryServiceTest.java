package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.service.dto.RecoveryPreparation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    @Mock
    private PaymentRecoveryStateService recoveryStateService;

    @Mock
    private PgClient pgClient;

    private PaymentRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new PaymentRecoveryService(recoveryStateService, pgClient);
    }

    @Test
    void recoverQueriesTossAndAppliesActualStatus() {
        RecoveryPreparation preparation = new RecoveryPreparation(1L, "payment-key", true, null);
        PgPaymentQueryResult queryResult = queryResult("DONE");
        PaymentResponse paid = paymentResponse(PaymentStatus.PAID);
        given(recoveryStateService.prepare(1L)).willReturn(preparation);
        given(pgClient.getPayment("payment-key")).willReturn(queryResult);
        given(recoveryStateService.apply(1L, queryResult)).willReturn(paid);

        PaymentResponse response = recoveryService.recover(1L);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void recoverLeavesProcessingStateWhenTossIsUnavailable() {
        RecoveryPreparation preparation = new RecoveryPreparation(1L, "payment-key", true, null);
        given(recoveryStateService.prepare(1L)).willReturn(preparation);
        given(pgClient.getPayment("payment-key"))
                .willThrow(new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE));

        assertThatThrownBy(() -> recoveryService.recover(1L))
                .isInstanceOf(CustomException.class);
        verify(recoveryStateService, never()).apply(1L, queryResult("DONE"));
    }

    private PgPaymentQueryResult queryResult(String status) {
        return new PgPaymentQueryResult(
                "payment-key", "gachisa_order", 12_600, status, PaymentMethod.CARD,
                null, null, 0);
    }

    private PaymentResponse paymentResponse(PaymentStatus status) {
        return new PaymentResponse(
                1L, 2L, 10L, null, "gachisa_order", "payment-key", 12_600, status,
                PaymentAttemptStatus.PAID, PaymentMethod.CARD, 1, null, null, null,
                null, null, null, null);
    }
}
