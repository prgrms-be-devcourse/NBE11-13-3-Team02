package com.gachisa.payment.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "토스페이먼츠 결제 상태 변경 웹훅 payload")
data class TossPaymentWebhookRequest(
    @field:Schema(description = "이벤트 종류", example = "PAYMENT_STATUS_CHANGED")
    @field:NotBlank val eventType: String,
    @field:Schema(description = "이벤트 발생 시각")
    @field:NotBlank val createdAt: String,
    @field:Schema(description = "결제 상세 정보")
    @field:Valid @field:NotNull val data: PaymentData,
) {
    data class PaymentData(
        @field:Schema(description = "토스페이먼츠 결제 키")
        @field:NotBlank val paymentKey: String,
        @field:Schema(description = "PG 주문번호")
        @field:NotBlank val orderId: String,
        @field:Schema(description = "결제 상태", example = "DONE")
        @field:NotBlank val status: String,
    )
}
