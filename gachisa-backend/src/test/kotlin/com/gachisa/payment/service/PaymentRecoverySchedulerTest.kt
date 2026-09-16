package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.repository.PaymentAttemptRepository
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
class PaymentRecoverySchedulerTest {
    @Mock private lateinit var attemptRepository: PaymentAttemptRepository
    @Mock private lateinit var recoveryService: PaymentRecoveryService
    @Mock private lateinit var timeProvider: TimeProvider
    private lateinit var scheduler: PaymentRecoveryScheduler

    @BeforeEach
    fun setUp() { scheduler = PaymentRecoveryScheduler(attemptRepository, recoveryService, timeProvider) }

    @Test
    fun schedulerContinuesAfterOneRecoveryFailure() {
        given(timeProvider.now()).willReturn(NOW)
        given(attemptRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(PaymentAttemptStatus.PROCESSING, NOW)).willReturn(listOf(attempt(1L), attempt(2L)))
        given(recoveryService.recover(1L)).willThrow(CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE))
        scheduler.recoverPendingPayments()
        verify(recoveryService).recover(1L)
        verify(recoveryService).recover(2L)
    }

    private fun attempt(id: Long): PaymentAttempt = PaymentAttempt(1L, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "order-$id", PaymentMethod.CARD, PaymentAttemptStatus.PROCESSING, 0, NOW.plusMinutes(10), NOW, NOW).also { ReflectionTestUtils.setField(it, "id", id) }
    companion object { private val NOW = LocalDateTime.of(2026, 8, 14, 12, 0) }
}
