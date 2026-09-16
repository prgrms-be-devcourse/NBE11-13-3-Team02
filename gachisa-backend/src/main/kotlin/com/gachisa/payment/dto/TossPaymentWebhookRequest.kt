package com.gachisa.payment.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class TossPaymentWebhookRequest(
    @field:NotBlank val eventType: String,
    @field:NotBlank val createdAt: String,
    @field:Valid @field:NotNull val data: PaymentData,
) {
    data class PaymentData(
        @field:NotBlank val paymentKey: String,
        @field:NotBlank val orderId: String,
        @field:NotBlank val status: String,
    )
}
