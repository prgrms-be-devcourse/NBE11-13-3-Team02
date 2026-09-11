package com.gachisa.payment.service;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.repository.TossWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TossWebhookStateService {

    private final TossWebhookEventRepository webhookEventRepository;
    private final PaymentRecoveryStateService recoveryStateService;
    private final TimeProvider timeProvider;

    @Transactional
    public boolean apply(String transmissionId, String eventType, PgPaymentQueryResult pgPayment) {
        int inserted = webhookEventRepository.insertIfAbsent(
                transmissionId,
                eventType,
                pgPayment.paymentKey(),
                timeProvider.now()
        );
        if (inserted == 0) {
            return false;
        }

        recoveryStateService.apply(findAttemptId(pgPayment), pgPayment);
        return true;
    }

    private Long findAttemptId(PgPaymentQueryResult pgPayment) {
        return recoveryStateService.findAttemptIdByPgOrderId(pgPayment.pgOrderId());
    }
}
