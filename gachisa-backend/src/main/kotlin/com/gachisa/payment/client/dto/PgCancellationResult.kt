package com.gachisa.payment.client.dto

data class PgCancellationResult(
    val paymentKey: String,
    val pgOrderId: String,
    val cancellationTransactionKey: String?,
    val cancelledAmount: Int,
)
