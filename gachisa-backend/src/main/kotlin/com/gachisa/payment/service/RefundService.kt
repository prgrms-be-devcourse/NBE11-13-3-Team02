package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.metric.PaymentMetrics
import org.springframework.stereotype.Service

@Service
class RefundService(
    private val refundStateService: RefundStateService,
    private val refundCompletionService: RefundCompletionService,
    private val pgClient: PgClient,
    private val paymentMetrics: PaymentMetrics,
) {
    fun refund(paymentId: Long, reason: String): RefundResponse {
        val requested = requestRefund(paymentId, reason)
        return processPending(requested.refundId)
    }

    fun requestRefund(paymentId: Long, reason: String): RefundResponse {
        val preparation = refundStateService.prepare(paymentId, reason)
        paymentMetrics.recordRefund(if (preparation.requestRequired) "requested" else "idempotent")
        return refundStateService.getRefund(preparation.refundId)
    }

    fun processPending(refundId: Long): RefundResponse {
        val preparation = refundStateService.claimPending(refundId)
        if (!preparation.requestRequired) return refundStateService.getRefund(refundId)

        try {
            val result = paymentMetrics.recordRefundTime { pgClient.cancel(
                preparation.paymentKey!!,
                preparation.reason,
                preparation.pgIdempotencyKey,
            ) }
            val response = refundCompletionService.complete(preparation.refundId, result)
            paymentMetrics.recordRefund("success")
            return response
        } catch (exception: CustomException) {
            if (exception.getErrorCode() == ErrorCode.PAYMENT_GATEWAY_REJECTED) {
                refundStateService.fail(preparation.refundId, exception.getErrorCode())
                paymentMetrics.recordRefund("failed")
            } else {
                refundStateService.keepPending(preparation.refundId, exception.getErrorCode())
                paymentMetrics.recordRefund("retry_scheduled")
            }
            throw exception
        }
    }
}
