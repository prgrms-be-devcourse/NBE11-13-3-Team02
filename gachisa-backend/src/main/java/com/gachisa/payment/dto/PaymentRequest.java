package com.gachisa.payment.dto;

import com.gachisa.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "결제 생성 요청. Idempotency-Key/Queue-Token 헤더와 함께 호출해야 함")
public record PaymentRequest(
        @Schema(description = "결제 수단", example = "CARD")
        @NotNull PaymentMethod paymentMethod
) {
}
