package com.gachisa.payment.service.dto;

import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;

public record RecoveryPreparation(
        Long paymentAttemptId,
        String paymentKey,
        boolean queryRequired,
        PaymentResponse existingResponse
) {
    public static RecoveryPreparation query(PaymentAttempt attempt) {
        return new RecoveryPreparation(attempt.getId(), attempt.getPgPaymentKey(), true, null);
    }

    public static RecoveryPreparation skip(Payment payment, PaymentAttempt attempt) {
        return new RecoveryPreparation(
                attempt.getId(), attempt.getPgPaymentKey(), false,
                PaymentResponse.from(payment, attempt));
    }
}
