package com.gachisa.payment.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.entity.RefundStatus
import com.gachisa.payment.service.dto.RefundPreparation
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class RefundServiceTest {

    private val PAYMENT_ID: Long = 1L
    private val REFUND_ID: Long = 2L
    private val PAYMENT_KEY: String = "payment-key"
    private val REASON: String = "공동구매 목표 인원 미달"
    private val IDEMPOTENCY_KEY: String = "4f775a4f-8eea-4f42-a494-8bba7ac3f402"

    @Mock
    lateinit var refundStateService: RefundStateService

    @Mock
    lateinit var refundCompletionService: RefundCompletionService

    @Mock
    lateinit var pgClient: PgClient

    private lateinit var refundService: RefundService

    @BeforeEach
    fun setUp() {
        refundService = RefundService(refundStateService, refundCompletionService, pgClient)
    }

    @Test
    fun refundCancelsPgPaymentAndCompletesRefund() {
        val preparation = RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, true)
        val cancellation = PgCancellationResult(
                PAYMENT_KEY, "gachisa_order", "cancel-transaction", 12_600)
        val completed = refundedResponse()

        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation)
        given(pgClient.cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)).willReturn(cancellation)
        given(refundCompletionService.complete(REFUND_ID, cancellation)).willReturn(completed)

        val response = refundService.processPending(REFUND_ID)

        assertThat(response.status).isEqualTo(RefundStatus.REFUNDED)
        verify(pgClient).cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)
    }

    @Test
    fun refundReturnsExistingResultWithoutCallingPgAgain() {
        val preparation = RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, false)
        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation)
        given(refundStateService.getRefund(REFUND_ID)).willReturn(refundedResponse())

        val response = refundService.processPending(REFUND_ID)

        assertThat(response.status).isEqualTo(RefundStatus.REFUNDED)
        verify(pgClient, never()).cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)
    }

    @Test
    fun temporaryPgFailureKeepsRefundPending() {
        val preparation = RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, true)
        val pgFailure = CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation)
        given(pgClient.cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)).willThrow(pgFailure)

        assertThatThrownBy { refundService.processPending(REFUND_ID) }
                .isSameAs(pgFailure)
        verify(refundStateService).keepPending(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        verify(refundStateService, never()).fail(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
    }

    @Test
    fun definitivePgRejectionFailsRefund() {
        val preparation = RefundPreparation(
                REFUND_ID, PAYMENT_KEY, REASON, IDEMPOTENCY_KEY, true)
        val pgFailure = CustomException(ErrorCode.PAYMENT_GATEWAY_REJECTED)
        given(refundStateService.claimPending(REFUND_ID)).willReturn(preparation)
        given(pgClient.cancel(PAYMENT_KEY, REASON, IDEMPOTENCY_KEY)).willThrow(pgFailure)

        assertThatThrownBy { refundService.processPending(REFUND_ID) }
                .isSameAs(pgFailure)
        verify(refundStateService).fail(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_REJECTED)
        verify(refundStateService, never()).keepPending(REFUND_ID, ErrorCode.PAYMENT_GATEWAY_REJECTED)
    }

    private fun refundedResponse(): RefundResponse {
        val now = LocalDateTime.of(2026, 8, 13, 12, 0)
        return RefundResponse(
                REFUND_ID,
                PAYMENT_ID,
                12_600,
                REASON,
                RefundStatus.REFUNDED,
                0,
                null,
                null,
                null,
                now.minusMinutes(1),
                now
        )
    }
}
