package com.gachisa.payment.service;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기열 입장이 만료되어 잡고 있던 결제 시도를 되돌린다.
 *
 * <p>대기열이 인프로세스였을 때는 스프링 이벤트로 받았지만, 별도 서비스가 되면서
 * 내부 HTTP 호출로 바뀌었다. 아직 시작하지 않은(READY) 시도만 만료시킨다 — 이미 PG로
 * 넘어간 건을 여기서 건드리면 결제와 상태가 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class PaymentAttemptExpiryService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final TimeProvider timeProvider;

    @Transactional
    public void expireIfReady(Long paymentAttemptId) {
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(paymentAttemptId)
                .orElse(null);
        if (attempt != null && attempt.getStatus() == PaymentAttemptStatus.READY) {
            attempt.expire(timeProvider.now());
        }
    }
}
