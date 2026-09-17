package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.metric.PaymentMetrics
import com.gachisa.payment.service.dto.RecoveryPreparation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PaymentRecoveryServiceTest {
    @Mock private lateinit var recoveryStateService: PaymentRecoveryStateService
    @Mock private lateinit var pgClient: PgClient
    @Mock private lateinit var paymentMetrics: PaymentMetrics
    private lateinit var recoveryService: PaymentRecoveryService
    @BeforeEach fun setUp() { recoveryService = PaymentRecoveryService(recoveryStateService, pgClient, paymentMetrics) }
    @Test fun recoverQueriesTossAndAppliesActualStatus() {
        val result = queryResult("DONE")
        given(recoveryStateService.prepare(1L)).willReturn(RecoveryPreparation(1L, "payment-key", true, null))
        given(pgClient.getPayment("payment-key")).willReturn(result)
        given(recoveryStateService.apply(1L, result)).willReturn(paymentResponse(PaymentStatus.PAID))
        assertThat(recoveryService.recover(1L).paymentStatus).isEqualTo(PaymentStatus.PAID)
    }
    @Test fun recoverLeavesProcessingStateWhenTossIsUnavailable() {
        given(recoveryStateService.prepare(1L)).willReturn(RecoveryPreparation(1L, "payment-key", true, null))
        given(pgClient.getPayment("payment-key")).willThrow(CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE))
        assertThatThrownBy { recoveryService.recover(1L) }.isInstanceOf(CustomException::class.java)
        verify(recoveryStateService, never()).apply(1L, queryResult("DONE"))
    }
    private fun queryResult(status: String) = PgPaymentQueryResult("payment-key", "gachisa_order", 12_600, status, PaymentMethod.CARD, null, null, 0)
    private fun paymentResponse(status: PaymentStatus) = PaymentResponse(1L, 2L, 10L, null, "gachisa_order", "payment-key", 12_600, status, PaymentAttemptStatus.PAID, PaymentMethod.CARD, 1, null, null, null, null, null, null, LocalDateTime.of(2026, 8, 18, 12, 0))
}
