package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.dto.PaymentResponse
import org.springframework.stereotype.Service

@Service
class PaymentRecoveryService(
    private val recoveryStateService: PaymentRecoveryStateService,
    private val pgClient: PgClient,
) {
    fun recover(paymentAttemptId: Long): PaymentResponse {
        val preparation = recoveryStateService.prepare(paymentAttemptId)
        if (!preparation.queryRequired) return preparation.existingResponse!!

        try {
            val result = pgClient.getPayment(preparation.paymentKey)
            return recoveryStateService.apply(paymentAttemptId, result)
        } catch (exception: CustomException) {
            recoveryStateService.recordFailure(paymentAttemptId, exception.getErrorCode())
            throw exception
        }
    }
}
