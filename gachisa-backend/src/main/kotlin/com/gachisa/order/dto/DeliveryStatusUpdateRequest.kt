package com.gachisa.order.dto

import com.gachisa.order.entity.DeliveryStatus
import jakarta.validation.constraints.NotNull

data class DeliveryStatusUpdateRequest(
    @field:NotNull val deliveryStatus: DeliveryStatus,
)
