package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.service.dto.RefundRecoveryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundRecoveryService {

    private final RefundStateService refundStateService;
    private final RefundCompletionService refundCompletionService;
    private final RefundService refundService;
    private final PgClient pgClient;

    public RefundResponse recover(Long refundId) {
        RefundRecoveryTarget target = refundStateService.getRecoveryTarget(refundId);
        PgPaymentQueryResult payment;
        try {
            payment = pgClient.getPayment(target.paymentKey());
        } catch (CustomException exception) {
            refundStateService.keepPending(refundId, exception.getErrorCode());
            throw exception;
        }

        if ("CANCELED".equals(payment.status())) {
            validateCancellation(target, payment);
            return refundCompletionService.complete(refundId, new PgCancellationResult(
                    payment.paymentKey(),
                    payment.pgOrderId(),
                    payment.cancellationTransactionKey(),
                    payment.cancelledAmount()
            ));
        }
        if ("DONE".equals(payment.status())) {
            refundStateService.retryPending(refundId);
            return refundService.processPending(refundId);
        }

        refundStateService.retryPending(refundId);
        return refundStateService.getRefund(refundId);
    }

    private void validateCancellation(RefundRecoveryTarget target, PgPaymentQueryResult payment) {
        if (!target.paymentKey().equals(payment.paymentKey())
                || !target.pgOrderId().equals(payment.pgOrderId())
                || target.amount() != payment.cancelledAmount()
                || payment.cancellationTransactionKey() == null) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }
    }
}
