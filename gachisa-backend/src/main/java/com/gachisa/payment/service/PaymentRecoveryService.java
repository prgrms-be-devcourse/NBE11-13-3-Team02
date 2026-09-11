package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.service.dto.RecoveryPreparation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRecoveryStateService recoveryStateService;
    private final PgClient pgClient;

    public PaymentResponse recover(Long paymentAttemptId) {
        RecoveryPreparation preparation = recoveryStateService.prepare(paymentAttemptId);
        if (!preparation.queryRequired()) {
            return preparation.existingResponse();
        }

        try {
            PgPaymentQueryResult result = pgClient.getPayment(preparation.paymentKey());
            return recoveryStateService.apply(paymentAttemptId, result);
        } catch (CustomException exception) {
            recoveryStateService.recordFailure(paymentAttemptId, exception.getErrorCode());
            throw exception;
        }
    }
}
