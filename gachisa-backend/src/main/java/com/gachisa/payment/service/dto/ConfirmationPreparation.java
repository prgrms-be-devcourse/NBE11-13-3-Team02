package com.gachisa.payment.service.dto;

import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentMethod;

public record ConfirmationPreparation(
        Long paymentAttemptId,
        String paymentKey,
        String pgOrderId,
        int amount,
        String pgIdempotencyKey,
        PaymentMethod paymentMethod,
        boolean requestRequired,
        PaymentResponse existingResponse
) {
    public static ConfirmationPreparation request(Payment payment, PaymentAttempt attempt) {
        return new ConfirmationPreparation(
                attempt.getId(), attempt.getPgPaymentKey(), attempt.getPgOrderId(), payment.getAmount(),
                attempt.getPgIdempotencyKey(), attempt.getPaymentMethod(), true, null);
    }

    public static ConfirmationPreparation existing(Payment payment, PaymentAttempt attempt) {
        return existing(payment, attempt, null);
    }

    public static ConfirmationPreparation existing(Payment payment, PaymentAttempt attempt, Long orderId) {
        return new ConfirmationPreparation(
                attempt.getId(), attempt.getPgPaymentKey(), attempt.getPgOrderId(), payment.getAmount(),
                attempt.getPgIdempotencyKey(), attempt.getPaymentMethod(), false,
                PaymentResponse.from(payment, attempt, orderId));
    }
}
