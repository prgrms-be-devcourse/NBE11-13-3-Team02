package com.gachisa.payment.service;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.queue.event.QueueAdmissionExpiredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentQueueEventHandler {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final TimeProvider timeProvider;

    @EventListener
    @Transactional
    public void expireReadyAttempt(QueueAdmissionExpiredEvent event) {
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(event.paymentAttemptId())
                .orElse(null);
        if (attempt != null && attempt.getStatus() == PaymentAttemptStatus.READY) {
            attempt.expire(timeProvider.now());
        }
    }
}
