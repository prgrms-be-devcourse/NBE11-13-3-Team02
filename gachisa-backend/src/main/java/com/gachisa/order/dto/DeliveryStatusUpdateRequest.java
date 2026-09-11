package com.gachisa.order.dto;

import com.gachisa.order.entity.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "주문 배송 상태 변경 요청 (관리자 전용)")
public record DeliveryStatusUpdateRequest(
        @Schema(description = "변경할 배송 상태. SHIPPING/DELIVERED/RETURNING/RETURNED로 변경하려면 " +
                "배송지가 먼저 등록되어 있어야 함", example = "PREPARING")
        @NotNull DeliveryStatus deliveryStatus
) {
}
