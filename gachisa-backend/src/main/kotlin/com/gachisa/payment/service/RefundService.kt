package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.dto.RefundResponse
import org.springframework.stereotype.Service

@Service
class RefundService(
    private val refundStateService: RefundStateService,
    private val refundCompletionService: RefundCompletionService,
    private val pgClient: PgClient,
) {
    fun refund(paymentId: Long, reason: String): RefundResponse {
        val requested = requestRefund(paymentId, reason)
        return processPending(requested.refundId)
    }

    fun requestRefund(paymentId: Long, reason: String): RefundResponse {
        val preparation = refundStateService.prepare(paymentId, reason)
        return refundStateService.getRefund(preparation.refundId)
    }

    fun processPending(refundId: Long): RefundResponse {
        val preparation = refundStateService.claimPending(refundId)
        if (!preparation.requestRequired) return refundStateService.getRefund(refundId)

        try {
            val result = pgClient.cancel(
                preparation.paymentKey!!,
                preparation.reason,
                preparation.pgIdempotencyKey,
            )
            return refundCompletionService.complete(preparation.refundId, result)
        } catch (exception: CustomException) {
            if (exception.getErrorCode() == ErrorCode.PAYMENT_GATEWAY_REJECTED) {
                refundStateService.fail(preparation.refundId, exception.getErrorCode())
            } else {
                refundStateService.keepPending(preparation.refundId, exception.getErrorCode())
            }
            throw exception
        }
    }
}
