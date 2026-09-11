package com.gachisa.payment.service.dto;

public record RefundRecoveryTarget(
        Long refundId,
        String paymentKey,
        String pgOrderId,
        int amount
) {
}
