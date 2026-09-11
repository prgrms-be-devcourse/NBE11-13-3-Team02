package com.gachisa.payment.dto;

public record PaymentCancellationResponse(
        String result,
        RefundResponse refund
) {

    public static PaymentCancellationResponse cancelled() {
        return new PaymentCancellationResponse("PARTICIPATION_CANCELLED", null);
    }

    public static PaymentCancellationResponse refundRequested(RefundResponse refund) {
        return new PaymentCancellationResponse("REFUND_REQUESTED", refund);
    }
}
