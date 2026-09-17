package com.gachisa.order.dto

import com.gachisa.order.entity.DeliveryStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "주문 배송 상태 변경 요청 (관리자 전용)")
data class DeliveryStatusUpdateRequest(
    @field:Schema(description = "변경할 배송 상태", example = "PREPARING")
    @field:NotNull val deliveryStatus: DeliveryStatus,
)
