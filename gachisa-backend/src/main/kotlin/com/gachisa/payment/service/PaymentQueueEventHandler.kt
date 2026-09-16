package com.gachisa.payment.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.queue.event.QueueAdmissionExpiredEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentQueueEventHandler(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val timeProvider: TimeProvider,
) {
    @EventListener
    @Transactional
    fun expireReadyAttempt(event: QueueAdmissionExpiredEvent) {
        val attempt = paymentAttemptRepository.findByIdForUpdate(event.paymentAttemptId).orElse(null)
        if (attempt != null && attempt.status == PaymentAttemptStatus.READY) {
            attempt.expire(timeProvider.now())
        }
    }
}
