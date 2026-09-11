package com.gachisa.payment.client.dto;

import com.gachisa.payment.entity.PaymentMethod;

public record PgPaymentQueryResult(
        String paymentKey,
        String pgOrderId,
        int amount,
        String status,
        PaymentMethod paymentMethod,
        String cancellationTransactionKey,
        String cancellationReason,
        int cancelledAmount
) {
}
