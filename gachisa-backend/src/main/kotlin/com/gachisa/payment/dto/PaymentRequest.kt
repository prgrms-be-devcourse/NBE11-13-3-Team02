package com.gachisa.payment.dto

import com.gachisa.payment.entity.PaymentMethod
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "결제 생성 요청")
data class PaymentRequest(
    @field:Schema(description = "결제 수단", example = "CARD")
    @field:NotNull val paymentMethod: PaymentMethod,
)
