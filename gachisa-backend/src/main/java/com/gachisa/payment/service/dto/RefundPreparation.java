package com.gachisa.payment.service.dto;

import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.Refund;

public record RefundPreparation(
        Long refundId,
        String paymentKey,
        String reason,
        String pgIdempotencyKey,
        boolean requestRequired
) {
    public static RefundPreparation request(Refund refund, PaymentAttempt attempt) {
        return new RefundPreparation(
                refund.getId(), attempt.getPgPaymentKey(), refund.getReason(),
                refund.getPgIdempotencyKey(), true);
    }

    public static RefundPreparation existing(Refund refund) {
        return new RefundPreparation(
                refund.getId(), null, refund.getReason(), refund.getPgIdempotencyKey(), false);
    }
}
