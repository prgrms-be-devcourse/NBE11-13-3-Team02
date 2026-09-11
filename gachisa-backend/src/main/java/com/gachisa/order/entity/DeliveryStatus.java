package com.gachisa.order.entity;

public enum DeliveryStatus {
    WAITING_FOR_GROUP_BUY,
    PREPARING,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    RETURNING,
    RETURNED;

    public boolean canChangeTo(DeliveryStatus newStatus) {
        return (this == WAITING_FOR_GROUP_BUY && newStatus == PREPARING)
                || (this == PREPARING && newStatus == SHIPPING)
                || (this == SHIPPING && newStatus == DELIVERED)
                || (this == WAITING_FOR_GROUP_BUY && newStatus == CANCELLED)
                || (this == PREPARING && newStatus == CANCELLED)
                || (this == SHIPPING && newStatus == RETURNING)
                || (this == DELIVERED && newStatus == RETURNING)
                || (this == RETURNING && newStatus == RETURNED);
    }
}
