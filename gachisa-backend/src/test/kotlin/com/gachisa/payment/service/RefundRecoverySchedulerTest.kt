package com.gachisa.payment.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.entity.Refund
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.repository.RefundRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RefundRecoverySchedulerTest {
    @Mock private lateinit var refundRepository: RefundRepository
    @Mock private lateinit var refundRecoveryService: RefundRecoveryService
    @Mock private lateinit var timeProvider: TimeProvider
    private lateinit var scheduler: RefundRecoveryScheduler

    @BeforeEach
    fun setUp() { scheduler = RefundRecoveryScheduler(refundRepository, refundRecoveryService, timeProvider) }

    @Test
    fun recoversPersistedPendingAndProcessingRefunds() {
        val refund = Refund(1L, 1000, "사유", RefundStatus.REFUND_PENDING, UUID.randomUUID().toString(), NOW, NOW).also { ReflectionTestUtils.setField(it, "id", 1L) }
        given(timeProvider.now()).willReturn(NOW)
        given(refundRepository.findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(listOf(RefundStatus.REFUND_PENDING, RefundStatus.PROCESSING), NOW)).willReturn(listOf(refund))
        scheduler.recoverPendingRefunds()
        verify(refundRecoveryService).recover(1L)
    }
    companion object { private val NOW = LocalDateTime.of(2026, 8, 18, 12, 0) }
}
