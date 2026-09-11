package com.gachisa.payment.entity;

public enum PaymentAttemptStatus {
    READY,
    PROCESSING,
    PAID,
    FAILED,
    RETRY_EXHAUSTED,
    EXPIRED,
    CANCELLED
}
