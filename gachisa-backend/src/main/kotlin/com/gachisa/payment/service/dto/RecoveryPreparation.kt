package com.gachisa.payment.service.dto

import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt

data class RecoveryPreparation(
    val paymentAttemptId: Long,
    val paymentKey: String,
    val queryRequired: Boolean,
    val existingResponse: PaymentResponse?,
) {
    companion object {
        fun query(attempt: PaymentAttempt): RecoveryPreparation = RecoveryPreparation(
            paymentAttemptId = attempt.id!!,
            paymentKey = attempt.pgPaymentKey!!,
            queryRequired = true,
            existingResponse = null,
        )

        fun skip(payment: Payment, attempt: PaymentAttempt): RecoveryPreparation = RecoveryPreparation(
            paymentAttemptId = attempt.id!!,
            paymentKey = attempt.pgPaymentKey!!,
            queryRequired = false,
            existingResponse = PaymentResponse.from(payment, attempt),
        )
    }
}
