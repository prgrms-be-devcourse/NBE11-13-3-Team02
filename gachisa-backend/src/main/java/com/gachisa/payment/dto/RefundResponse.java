package com.gachisa.payment.dto;

import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import java.time.LocalDateTime;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        int amount,
        String reason,
        RefundStatus status,
        int retryCount,
        LocalDateTime nextRetryAt,
        String failureCode,
        String failureMessage,
        LocalDateTime requestedAt,
        LocalDateTime refundedAt
) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getRetryCount(),
                refund.getNextRetryAt(),
                refund.getFailureCode(),
                refund.getFailureMessage(),
                refund.getRequestedAt(),
                refund.getRefundedAt()
        );
    }
}
