package com.gachisa.payment.service.dto

import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentMethod

data class ConfirmationPreparation(
    val paymentAttemptId: Long,
    val paymentKey: String,
    val pgOrderId: String,
    val amount: Int,
    val pgIdempotencyKey: String,
    val paymentMethod: PaymentMethod,
    val requestRequired: Boolean,
    val existingResponse: PaymentResponse?,
) {
    companion object {
        fun request(payment: Payment, attempt: PaymentAttempt): ConfirmationPreparation = ConfirmationPreparation(
            paymentAttemptId = attempt.id!!,
            paymentKey = attempt.pgPaymentKey!!,
            pgOrderId = attempt.pgOrderId,
            amount = payment.amount,
            pgIdempotencyKey = attempt.pgIdempotencyKey,
            paymentMethod = attempt.paymentMethod,
            requestRequired = true,
            existingResponse = null,
        )

        fun existing(payment: Payment, attempt: PaymentAttempt): ConfirmationPreparation =
            existing(payment, attempt, null)

        fun existing(payment: Payment, attempt: PaymentAttempt, orderId: Long?): ConfirmationPreparation =
            ConfirmationPreparation(
                paymentAttemptId = attempt.id!!,
                paymentKey = attempt.pgPaymentKey!!,
                pgOrderId = attempt.pgOrderId,
                amount = payment.amount,
                pgIdempotencyKey = attempt.pgIdempotencyKey,
                paymentMethod = attempt.paymentMethod,
                requestRequired = false,
                existingResponse = PaymentResponse.from(payment, attempt, orderId),
            )
    }
}
