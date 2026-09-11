package com.gachisa.payment.client.dto;

import com.gachisa.payment.entity.PaymentMethod;

public record PgConfirmationResult(
        String pgTransactionId,
        String pgOrderId,
        int amount,
        PaymentMethod paymentMethod
) {
}
