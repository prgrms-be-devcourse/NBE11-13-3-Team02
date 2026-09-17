package com.gachisa.payment.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.repository.TossWebhookEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TossWebhookStateService(
    private val webhookEventRepository: TossWebhookEventRepository,
    private val recoveryStateService: PaymentRecoveryStateService,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun apply(transmissionId: String, eventType: String, pgPayment: PgPaymentQueryResult): Boolean {
        val inserted = webhookEventRepository.insertIfAbsent(
            transmissionId,
            eventType,
            pgPayment.paymentKey,
            timeProvider.now(),
        )
        if (inserted == 0) return false

        recoveryStateService.apply(findAttemptId(pgPayment), pgPayment)
        return true
    }

    private fun findAttemptId(pgPayment: PgPaymentQueryResult): Long =
        recoveryStateService.findAttemptIdByPgOrderId(pgPayment.pgOrderId)
}
