package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.dto.TossPaymentWebhookRequest
import com.gachisa.payment.dto.TossWebhookResponse
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils

@Service
class TossWebhookService(
    private val pgClient: PgClient,
    private val webhookStateService: TossWebhookStateService,
) {
    companion object {
        private const val PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED"
    }

    fun process(transmissionId: String?, request: TossPaymentWebhookRequest): TossWebhookResponse {
        if (!StringUtils.hasText(transmissionId)) throw CustomException(ErrorCode.INVALID_REQUEST)
        if (request.eventType != PAYMENT_STATUS_CHANGED) return TossWebhookResponse(false)

        val pgPayment = pgClient.getPayment(request.data.paymentKey)
        validateWebhookBody(request, pgPayment)
        return TossWebhookResponse(
            webhookStateService.apply(transmissionId!!, request.eventType, pgPayment),
        )
    }

    private fun validateWebhookBody(request: TossPaymentWebhookRequest, pgPayment: PgPaymentQueryResult) {
        if (request.data.paymentKey != pgPayment.paymentKey ||
            request.data.orderId != pgPayment.pgOrderId ||
            request.data.status != pgPayment.status
        ) {
            throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
        }
    }
}
