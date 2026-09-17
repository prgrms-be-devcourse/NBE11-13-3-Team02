package com.gachisa.payment.entity

enum class RefundStatus {
    REFUND_PENDING,
    PROCESSING,
    REFUNDED,
    FAILED,
    RETRY_EXHAUSTED,
}
