package com.gachisa.payment.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentRecoveryScheduler(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val paymentRecoveryService: PaymentRecoveryService,
    private val timeProvider: TimeProvider,
) {
    private val log = LoggerFactory.getLogger(PaymentRecoveryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${payment.recovery.fixed-delay-ms:60000}")
    fun recoverPendingPayments() {
        val pendingAttempts = paymentAttemptRepository
            .findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                PaymentAttemptStatus.PROCESSING,
                timeProvider.now(),
            )

        for (attempt in pendingAttempts) {
            try {
                paymentRecoveryService.recover(attempt.id!!)
            } catch (exception: RuntimeException) {
                val latest = paymentAttemptRepository.findById(attempt.id!!).orElse(attempt)
                log.warn(
                    "결제 복구 실패. paymentAttemptId={}, retryCount={}, failureCode={}, nextRetryAt={}",
                    latest.id,
                    latest.retryCount,
                    latest.failureCode,
                    latest.nextRetryAt,
                    exception,
                )
            }
        }
    }
}
