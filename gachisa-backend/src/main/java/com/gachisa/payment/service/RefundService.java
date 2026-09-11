package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.service.dto.RefundPreparation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundStateService refundStateService;
    private final RefundCompletionService refundCompletionService;
    private final PgClient pgClient;

    public RefundResponse refund(Long paymentId, String reason) {
        RefundResponse requested = requestRefund(paymentId, reason);
        return processPending(requested.refundId());
    }

    public RefundResponse requestRefund(Long paymentId, String reason) {
        RefundPreparation preparation = refundStateService.prepare(paymentId, reason);
        return refundStateService.getRefund(preparation.refundId());
    }

    public RefundResponse processPending(Long refundId) {
        RefundPreparation preparation = refundStateService.claimPending(refundId);
        if (!preparation.requestRequired()) {
            return refundStateService.getRefund(refundId);
        }

        try {
            PgCancellationResult result = pgClient.cancel(
                    preparation.paymentKey(),
                    preparation.reason(),
                    preparation.pgIdempotencyKey()
            );
            return refundCompletionService.complete(preparation.refundId(), result);
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.PAYMENT_GATEWAY_REJECTED) {
                refundStateService.fail(preparation.refundId(), exception.getErrorCode());
            } else {
                refundStateService.keepPending(preparation.refundId(), exception.getErrorCode());
            }
            throw exception;
        }
    }
}
