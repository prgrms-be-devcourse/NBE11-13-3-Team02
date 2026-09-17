package com.gachisa.order.dto

import com.gachisa.order.entity.DeliveryStatus
import com.gachisa.order.entity.Order
import java.time.LocalDateTime

data class DeliveryResponse(
    val orderId: Long,
    val orderNumber: String,
    val productId: Long,
    val productName: String,
    val productImageUrl: String?,
    val quantity: Int,
    val amount: Int,
    val deliveryStatus: DeliveryStatus,
    val recipientName: String?,
    val recipientPhone: String?,
    val zipCode: String?,
    val address: String?,
    val addressDetail: String?,
    val deliveryRequest: String?,
    val carrier: String,
    val trackingNumber: String?,
    val preparationStartedAt: LocalDateTime?,
    val shippingStartedAt: LocalDateTime?,
    val expectedDeliveryAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
) {
    companion object {
        private const val SELF_DELIVERY = "자체배송"

        fun from(order: Order): DeliveryResponse {
            val shippingStartedAt = order.shippingStartedAt
            val preparationStartedAt = order.preparationStartedAt
            val expectedDeliveryAt = if (shippingStartedAt != null) {
                shippingStartedAt.plusDays(2)
            } else if (preparationStartedAt != null) {
                preparationStartedAt.plusDays(3)
            } else {
                null
            }

            return DeliveryResponse(
                orderId = order.id!!,
                orderNumber = order.orderNumber,
                productId = order.productId,
                productName = order.productName,
                productImageUrl = order.productImageUrl,
                quantity = order.quantity,
                amount = order.amount,
                deliveryStatus = order.deliveryStatus,
                recipientName = order.recipientName,
                recipientPhone = order.recipientPhone,
                zipCode = order.zipCode,
                address = order.address,
                addressDetail = order.addressDetail,
                deliveryRequest = order.deliveryRequest,
                carrier = SELF_DELIVERY,
                trackingNumber = null,
                preparationStartedAt = order.preparationStartedAt,
                shippingStartedAt = order.shippingStartedAt,
                expectedDeliveryAt = expectedDeliveryAt,
                deliveredAt = order.deliveredAt,
            )
        }
    }
}
