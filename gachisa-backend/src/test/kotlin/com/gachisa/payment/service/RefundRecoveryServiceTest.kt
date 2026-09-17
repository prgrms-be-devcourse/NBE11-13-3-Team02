package com.gachisa.payment.service

import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.service.dto.RefundRecoveryTarget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class RefundRecoveryServiceTest {
    @Mock private lateinit var refundStateService: RefundStateService
    @Mock private lateinit var refundCompletionService: RefundCompletionService
    @Mock private lateinit var refundService: RefundService
    @Mock private lateinit var pgClient: PgClient
    private lateinit var recoveryService: RefundRecoveryService
    @BeforeEach fun setUp() { recoveryService = RefundRecoveryService(refundStateService, refundCompletionService, refundService, pgClient) }
    @Test fun cancelledPaymentCompletesPendingRefund() {
        val target = RefundRecoveryTarget(1L, "payment-key", "order-id", 12_600)
        val result = PgPaymentQueryResult("payment-key", "order-id", 12_600, "CANCELED", PaymentMethod.CARD, "cancel-transaction", "목표 미달", 12_600)
        given(refundStateService.getRecoveryTarget(1L)).willReturn(target)
        given(pgClient.getPayment("payment-key")).willReturn(result)
        given(refundCompletionService.complete(1L, PgCancellationResult("payment-key", "order-id", "cancel-transaction", 12_600))).willReturn(refundResponse(RefundStatus.REFUNDED))
        assertThat(recoveryService.recover(1L).status).isEqualTo(RefundStatus.REFUNDED)
    }
    @Test fun paymentStillDoneRetriesCancellationWithSameRefund() {
        given(refundStateService.getRecoveryTarget(1L)).willReturn(RefundRecoveryTarget(1L, "payment-key", "order-id", 12_600))
        given(pgClient.getPayment("payment-key")).willReturn(PgPaymentQueryResult("payment-key", "order-id", 12_600, "DONE", PaymentMethod.CARD, null, null, 0))
        given(refundService.processPending(1L)).willReturn(refundResponse(RefundStatus.REFUNDED))
        recoveryService.recover(1L)
        verify(refundStateService).retryPending(1L)
        verify(refundService).processPending(1L)
    }
    private fun refundResponse(status: RefundStatus) = RefundResponse(1L, 10L, 12_600, "목표 미달", status, 0, null, null, null, LocalDateTime.of(2026, 8, 18, 12, 0), null)
}
