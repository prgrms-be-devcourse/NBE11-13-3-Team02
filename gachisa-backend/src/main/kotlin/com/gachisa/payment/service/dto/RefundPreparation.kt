package com.gachisa.payment.service.dto

import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.Refund

data class RefundPreparation(
    val refundId: Long,
    val paymentKey: String?,
    val reason: String,
    val pgIdempotencyKey: String,
    val requestRequired: Boolean,
) {
    companion object {
        fun request(refund: Refund, attempt: PaymentAttempt): RefundPreparation = RefundPreparation(
            refundId = refund.id!!,
            paymentKey = attempt.pgPaymentKey,
            reason = refund.reason,
            pgIdempotencyKey = refund.pgIdempotencyKey,
            requestRequired = true,
        )

        fun existing(refund: Refund): RefundPreparation = RefundPreparation(
            refundId = refund.id!!,
            paymentKey = null,
            reason = refund.reason,
            pgIdempotencyKey = refund.pgIdempotencyKey,
            requestRequired = false,
        )
    }
}
