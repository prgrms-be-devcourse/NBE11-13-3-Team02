package com.gachisa.payment.client.dto

import com.gachisa.payment.entity.PaymentMethod

data class PgPaymentQueryResult(
    val paymentKey: String,
    val pgOrderId: String,
    val amount: Int,
    val status: String,
    val paymentMethod: PaymentMethod?,
    val cancellationTransactionKey: String?,
    val cancellationReason: String?,
    val cancelledAmount: Int,
)
