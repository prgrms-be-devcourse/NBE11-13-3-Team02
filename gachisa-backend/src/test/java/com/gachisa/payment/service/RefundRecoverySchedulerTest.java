package com.gachisa.payment.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.repository.RefundRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundRecoverySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 12, 0);

    @Mock RefundRepository refundRepository;
    @Mock RefundRecoveryService refundRecoveryService;
    @Mock TimeProvider timeProvider;
    private RefundRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RefundRecoveryScheduler(refundRepository, refundRecoveryService, timeProvider);
    }

    @Test
    void recoversPersistedPendingAndProcessingRefunds() {
        Refund refund = Refund.builder()
                .status(RefundStatus.REFUND_PENDING)
                .build();
        ReflectionTestUtils.setField(refund, "id", 1L);
        given(timeProvider.now()).willReturn(NOW);
        given(refundRepository.findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                List.of(RefundStatus.REFUND_PENDING, RefundStatus.PROCESSING),
                NOW))
                .willReturn(List.of(refund));

        scheduler.recoverPendingRefunds();

        verify(refundRecoveryService).recover(1L);
    }
}
