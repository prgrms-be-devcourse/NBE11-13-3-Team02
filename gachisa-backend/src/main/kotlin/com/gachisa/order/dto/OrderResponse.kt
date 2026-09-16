package com.gachisa.order.dto

import com.gachisa.order.entity.DeliveryStatus
import com.gachisa.order.entity.Order
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResponse(
    val orderId: Long,
    val orderNumber: String,
    val participationId: Long,
    val paymentId: Long,
    val groupBuyId: Long,
    val productId: Long,
    val productName: String,
    val productImageUrl: String?,
    val quantity: Int,
    val basePrice: Int,
    val discountRate: BigDecimal,
    val discountAmount: Int,
    val amount: Int,
    val deliveryAddressRegistered: Boolean,
    val deliveryStatus: DeliveryStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(order: Order): OrderResponse = OrderResponse(
            orderId = order.id!!,
            orderNumber = order.orderNumber,
            participationId = order.participationId,
            paymentId = order.paymentId,
            groupBuyId = order.groupBuyId,
            productId = order.productId,
            productName = order.productName,
            productImageUrl = order.productImageUrl,
            quantity = order.quantity,
            basePrice = order.basePrice,
            discountRate = order.discountRate,
            discountAmount = order.discountAmount,
            amount = order.amount,
            deliveryAddressRegistered = order.address != null,
            deliveryStatus = order.deliveryStatus,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}
