package com.gachisa.order.dto

data class OrderCreateCommand(
    val participationId: Long,
    val paymentId: Long,
    val buyerId: Long,
    val groupBuyId: Long,
    val quantity: Int,
    val amount: Int,
)
