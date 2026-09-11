package com.gachisa.payment.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentRecoverySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 12, 0);
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock PaymentRecoveryService recoveryService;
    @Mock TimeProvider timeProvider;
    private PaymentRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentRecoveryScheduler(attemptRepository, recoveryService, timeProvider);
    }

    @Test
    void schedulerContinuesAfterOneRecoveryFailure() {
        PaymentAttempt first = attempt(1L);
        PaymentAttempt second = attempt(2L);
        given(timeProvider.now()).willReturn(NOW);
        given(attemptRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                PaymentAttemptStatus.PROCESSING, NOW))
                .willReturn(List.of(first, second));
        given(recoveryService.recover(1L))
                .willThrow(new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE));

        scheduler.recoverPendingPayments();

        verify(recoveryService).recover(1L);
        verify(recoveryService).recover(2L);
    }

    private PaymentAttempt attempt(Long id) {
        PaymentAttempt attempt = PaymentAttempt.builder().build();
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }
}
