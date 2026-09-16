package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.dto.TossPaymentWebhookRequest
import com.gachisa.payment.entity.PaymentMethod
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class TossWebhookServiceTest {
    @Mock private lateinit var pgClient: PgClient
    @Mock private lateinit var webhookStateService: TossWebhookStateService
    private lateinit var webhookService: TossWebhookService
    @BeforeEach fun setUp() { webhookService = TossWebhookService(pgClient, webhookStateService) }
    @Test fun webhookVerifiesPayloadWithTossQueryBeforeApplyingState() {
        val request = request("DONE"); val result = queryResult("DONE")
        given(pgClient.getPayment(PAYMENT_KEY)).willReturn(result)
        given(webhookStateService.apply(TRANSMISSION_ID, "PAYMENT_STATUS_CHANGED", result)).willReturn(true)
        assertThat(webhookService.process(TRANSMISSION_ID, request).processed).isTrue()
        verify(webhookStateService).apply(TRANSMISSION_ID, "PAYMENT_STATUS_CHANGED", result)
    }
    @Test fun webhookRejectsStatusThatDiffersFromTossQuery() {
        given(pgClient.getPayment(PAYMENT_KEY)).willReturn(queryResult("ABORTED"))
        assertThatThrownBy { webhookService.process(TRANSMISSION_ID, request("DONE")) }.isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).getErrorCode() }.isEqualTo(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
    }
    @Test fun webhookRejectsMissingTransmissionId() {
        assertThatThrownBy { webhookService.process(null, request("DONE")) }.isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).getErrorCode() }.isEqualTo(ErrorCode.INVALID_REQUEST)
    }
    private fun request(status: String) = TossPaymentWebhookRequest("PAYMENT_STATUS_CHANGED", "2026-08-13T12:00:00.000000", TossPaymentWebhookRequest.PaymentData(PAYMENT_KEY, "gachisa_order", status))
    private fun queryResult(status: String) = PgPaymentQueryResult(PAYMENT_KEY, "gachisa_order", 12_600, status, PaymentMethod.CARD, null, null, 0)
    companion object { private const val TRANSMISSION_ID = "transmission-id"; private const val PAYMENT_KEY = "payment-key" }
}
