package com.gachisa.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class PaymentConfirmRequest(
    @field:NotBlank val paymentKey: String,
    @field:NotBlank val pgOrderId: String,
    @field:Positive val amount: Int,
)
