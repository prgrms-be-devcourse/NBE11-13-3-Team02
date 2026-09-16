package com.gachisa.payment.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.repository.RefundRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RefundRecoveryScheduler(
    private val refundRepository: RefundRepository,
    private val refundRecoveryService: RefundRecoveryService,
    private val timeProvider: TimeProvider,
) {
    private val log = LoggerFactory.getLogger(RefundRecoveryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${payment.refund-recovery.fixed-delay-ms:5000}")
    fun recoverPendingRefunds() {
        val pendingRefunds = refundRepository
            .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                listOf(RefundStatus.REFUND_PENDING, RefundStatus.PROCESSING),
                timeProvider.now(),
            )

        for (refund in pendingRefunds) {
            try {
                refundRecoveryService.recover(refund.id!!)
            } catch (exception: RuntimeException) {
                val latest = refundRepository.findById(refund.id!!).orElse(refund)
                log.warn(
                    "환불 복구 실패. refundId={}, retryCount={}, failureCode={}, nextRetryAt={}",
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
