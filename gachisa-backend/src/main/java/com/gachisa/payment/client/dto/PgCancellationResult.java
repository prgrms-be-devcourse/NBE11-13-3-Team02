package com.gachisa.payment.client.dto;

public record PgCancellationResult(
        String paymentKey,
        String pgOrderId,
        String cancellationTransactionKey,
        int cancelledAmount
) {
}
