package com.gachisa.payment.entity;

public enum RefundStatus {
    REFUND_PENDING,
    PROCESSING,
    REFUNDED,
    FAILED,
    RETRY_EXHAUSTED
}
