package com.gachisa.order.dto

import com.gachisa.order.entity.Order
import org.springframework.data.domain.Page

data class OrderListResponse(
    val content: List<OrderResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun from(orders: Page<Order>): OrderListResponse = OrderListResponse(
            content = orders.content.map(OrderResponse::from),
            page = orders.number,
            size = orders.size,
            totalElements = orders.totalElements,
            totalPages = orders.totalPages,
        )
    }
}
