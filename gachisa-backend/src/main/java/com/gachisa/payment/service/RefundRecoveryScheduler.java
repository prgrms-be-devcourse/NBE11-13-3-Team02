package com.gachisa.payment.service;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.repository.RefundRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRecoveryScheduler {

    private final RefundRepository refundRepository;
    private final RefundRecoveryService refundRecoveryService;
    private final TimeProvider timeProvider;

    @Scheduled(fixedDelayString = "${payment.refund-recovery.fixed-delay-ms:5000}")
    public void recoverPendingRefunds() {
        List<Refund> pendingRefunds = refundRepository
                .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        List.of(RefundStatus.REFUND_PENDING, RefundStatus.PROCESSING),
                        timeProvider.now()
                );

        for (Refund refund : pendingRefunds) {
            try {
                refundRecoveryService.recover(refund.getId());
            } catch (RuntimeException exception) {
                Refund latest = refundRepository.findById(refund.getId()).orElse(refund);
                log.warn(
                        "환불 복구 실패. refundId={}, retryCount={}, failureCode={}, nextRetryAt={}",
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
