package com.gachisa.order.controller

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.order.dto.DeliveryAddressRequest
import com.gachisa.order.dto.DeliveryResponse
import com.gachisa.order.dto.OrderListResponse
import com.gachisa.order.dto.OrderResponse
import com.gachisa.order.service.OrderService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {
    @GetMapping
    fun getMyOrders(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): OrderListResponse = orderService.getMyOrders(requireUserId(userId), page, size)

    @GetMapping("/{orderId}")
    fun getMyOrder(
        @PathVariable orderId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): OrderResponse = orderService.getMyOrder(orderId, requireUserId(userId))

    @GetMapping("/by-participation/{participationId}")
    fun getMyOrderByParticipation(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): OrderResponse = orderService.getMyOrderByParticipation(participationId, requireUserId(userId))

    @PostMapping("/{orderId}/delivery-address")
    fun registerDeliveryAddress(
        @PathVariable orderId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: DeliveryAddressRequest,
    ): DeliveryResponse = orderService.registerDeliveryAddress(orderId, requireUserId(userId), request)

    @GetMapping("/{orderId}/delivery")
    fun getMyDelivery(
        @PathVariable orderId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): DeliveryResponse = orderService.getMyDelivery(orderId, requireUserId(userId))

    private fun requireUserId(userId: Long?): Long =
        userId ?: throw CustomException(ErrorCode.FORBIDDEN)
}
