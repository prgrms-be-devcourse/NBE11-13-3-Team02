package com.gachisa.order.entity

enum class DeliveryStatus {
    WAITING_FOR_GROUP_BUY,
    PREPARING,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    RETURNING,
    RETURNED;

    fun canChangeTo(newStatus: DeliveryStatus): Boolean =
        (this == WAITING_FOR_GROUP_BUY && newStatus == PREPARING) ||
            (this == PREPARING && newStatus == SHIPPING) ||
            (this == SHIPPING && newStatus == DELIVERED) ||
            (this == WAITING_FOR_GROUP_BUY && newStatus == CANCELLED) ||
            (this == PREPARING && newStatus == CANCELLED) ||
            (this == SHIPPING && newStatus == RETURNING) ||
            (this == DELIVERED && newStatus == RETURNING) ||
            (this == RETURNING && newStatus == RETURNED)
}
