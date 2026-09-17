package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.metric.PaymentMetrics
import org.springframework.stereotype.Service

@Service
class PaymentRecoveryService(
    private val recoveryStateService: PaymentRecoveryStateService,
    private val pgClient: PgClient,
    private val paymentMetrics: PaymentMetrics,
) {
    fun recover(paymentAttemptId: Long): PaymentResponse {
        val preparation = recoveryStateService.prepare(paymentAttemptId)
        if (!preparation.queryRequired) {
            paymentMetrics.recordPaymentRecovery("skipped")
            return preparation.existingResponse!!
        }

        try {
            val result = pgClient.getPayment(preparation.paymentKey)
            val response = recoveryStateService.apply(paymentAttemptId, result)
            paymentMetrics.recordPaymentRecovery("success")
            return response
        } catch (exception: CustomException) {
            recoveryStateService.recordFailure(paymentAttemptId, exception.getErrorCode())
            paymentMetrics.recordPaymentRecovery("failed")
            throw exception
        }
    }
}
