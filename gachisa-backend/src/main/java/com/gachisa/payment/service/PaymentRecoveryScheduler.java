package com.gachisa.payment.service;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryScheduler {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRecoveryService paymentRecoveryService;
    private final TimeProvider timeProvider;

    @Scheduled(fixedDelayString = "${payment.recovery.fixed-delay-ms:60000}")
    public void recoverPendingPayments() {
        List<PaymentAttempt> pendingAttempts = paymentAttemptRepository
                .findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                PaymentAttemptStatus.PROCESSING,
                timeProvider.now()
        );

        for (PaymentAttempt attempt : pendingAttempts) {
            try {
                paymentRecoveryService.recover(attempt.getId());
            } catch (RuntimeException exception) {
                PaymentAttempt latest = paymentAttemptRepository.findById(attempt.getId())
                        .orElse(attempt);
                log.warn(
                        "결제 복구 실패. paymentAttemptId={}, retryCount={}, failureCode={}, nextRetryAt={}",
                        latest.getId(),
                        latest.getRetryCount(),
                        latest.getFailureCode(),
                        latest.getNextRetryAt(),
                        exception
                );
            }
        }
    }
}
