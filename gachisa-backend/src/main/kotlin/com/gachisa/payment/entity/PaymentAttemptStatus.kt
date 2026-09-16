package com.gachisa.payment.entity

enum class PaymentAttemptStatus {
    READY,
    PROCESSING,
    PAID,
    FAILED,
    RETRY_EXHAUSTED,
    EXPIRED,
    CANCELLED,
}
