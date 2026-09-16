package com.gachisa.order.controller

import com.gachisa.order.dto.DeliveryResponse
import com.gachisa.order.dto.DeliveryStatusUpdateRequest
import com.gachisa.order.service.OrderService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrderController(private val orderService: OrderService) {
    @PatchMapping("/{orderNumber}/delivery-status")
    fun updateDeliveryStatus(
        @PathVariable orderNumber: String,
        @Valid @RequestBody request: DeliveryStatusUpdateRequest,
    ): DeliveryResponse = orderService.updateDeliveryStatusByAdmin(orderNumber, request.deliveryStatus)
}
