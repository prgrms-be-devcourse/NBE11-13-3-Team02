package com.gachisa.payment.client.dto

import com.gachisa.payment.entity.PaymentMethod

data class PgConfirmationResult(
    val pgTransactionId: String,
    val pgOrderId: String,
    val amount: Int,
    val paymentMethod: PaymentMethod?,
)
