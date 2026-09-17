package com.gachisa.payment.service.dto

data class RefundRecoveryTarget(
    val refundId: Long,
    val paymentKey: String,
    val pgOrderId: String,
    val amount: Int,
)
