package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.TossPaymentWebhookRequest;
import com.gachisa.payment.dto.TossWebhookResponse;
import com.gachisa.payment.entity.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TossWebhookServiceTest {

    private static final String TRANSMISSION_ID = "transmission-id";
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private PgClient pgClient;

    @Mock
    private TossWebhookStateService webhookStateService;

    private TossWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new TossWebhookService(pgClient, webhookStateService);
    }

    @Test
    void webhookVerifiesPayloadWithTossQueryBeforeApplyingState() {
        TossPaymentWebhookRequest request = request("DONE");
        PgPaymentQueryResult queryResult = queryResult("DONE");
        given(pgClient.getPayment(PAYMENT_KEY)).willReturn(queryResult);
        given(webhookStateService.apply(
                TRANSMISSION_ID, "PAYMENT_STATUS_CHANGED", queryResult)).willReturn(true);

        TossWebhookResponse response = webhookService.process(TRANSMISSION_ID, request);

        assertThat(response.processed()).isTrue();
        verify(webhookStateService).apply(
                TRANSMISSION_ID, "PAYMENT_STATUS_CHANGED", queryResult);
    }

    @Test
    void webhookRejectsStatusThatDiffersFromTossQuery() {
        TossPaymentWebhookRequest request = request("DONE");
        given(pgClient.getPayment(PAYMENT_KEY)).willReturn(queryResult("ABORTED"));

        assertThatThrownBy(() -> webhookService.process(TRANSMISSION_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
    }

    @Test
    void webhookRejectsMissingTransmissionId() {
        assertThatThrownBy(() -> webhookService.process(null, request("DONE")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private TossPaymentWebhookRequest request(String status) {
        return new TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                "2026-08-13T12:00:00.000000",
                new TossPaymentWebhookRequest.PaymentData(PAYMENT_KEY, "gachisa_order", status)
        );
    }

    private PgPaymentQueryResult queryResult(String status) {
        return new PgPaymentQueryResult(
                PAYMENT_KEY,
                "gachisa_order",
                12_600,
                status,
                PaymentMethod.CARD,
                null,
                null,
                0
        );
    }
}
