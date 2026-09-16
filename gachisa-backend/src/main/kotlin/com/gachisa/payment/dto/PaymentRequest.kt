package com.gachisa.payment.dto

import com.gachisa.payment.entity.PaymentMethod
import jakarta.validation.constraints.NotNull

data class PaymentRequest(
    @field:NotNull val paymentMethod: PaymentMethod,
)
