package com.gachisa.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "토스페이먼츠가 결제 상태 변경 시 서버로 보내는 웹훅 payload (프론트에서 직접 호출하지 않음)")
public record TossPaymentWebhookRequest(
        @Schema(description = "이벤트 종류", example = "PAYMENT_STATUS_CHANGED")
        @NotBlank String eventType,
        @Schema(description = "이벤트 발생 시각 (ISO-8601)")
        @NotBlank String createdAt,
        @Schema(description = "결제 상세 정보")
        @Valid @NotNull PaymentData data
) {

    public record PaymentData(
            @Schema(description = "토스페이먼츠 결제 키")
            @NotBlank String paymentKey,
            @Schema(description = "PG 주문번호")
            @NotBlank String orderId,
            @Schema(description = "결제 상태", example = "DONE")
            @NotBlank String status
    ) {
    }
}
