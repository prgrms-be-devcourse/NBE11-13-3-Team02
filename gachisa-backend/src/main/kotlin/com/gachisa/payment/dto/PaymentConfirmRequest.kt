package com.gachisa.payment.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

@Schema(description = "PG 결제창에서 돌아온 뒤 서버에 최종 승인을 요청할 때 보내는 값")
data class PaymentConfirmRequest(
    @field:Schema(description = "토스페이먼츠가 발급한 결제 키")
    @field:NotBlank val paymentKey: String,
    @field:Schema(description = "결제 생성 시 발급된 PG 주문번호")
    @field:NotBlank val pgOrderId: String,
    @field:Schema(description = "결제 금액(원)", example = "18000")
    @field:Positive val amount: Int,
)
