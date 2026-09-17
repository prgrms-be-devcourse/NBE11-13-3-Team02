package com.gachisa.payment.dto

import com.gachisa.payment.entity.Refund
import com.gachisa.payment.entity.RefundStatus
import java.time.LocalDateTime

data class RefundResponse(
    val refundId: Long,
    val paymentId: Long,
    val amount: Int,
    val reason: String,
    val status: RefundStatus,
    val retryCount: Int,
    val nextRetryAt: LocalDateTime?,
    val failureCode: String?,
    val failureMessage: String?,
    val requestedAt: LocalDateTime,
    val refundedAt: LocalDateTime?,
) {
    companion object {
        fun from(refund: Refund): RefundResponse = RefundResponse(
            refundId = refund.id!!,
            paymentId = refund.paymentId,
            amount = refund.amount,
            reason = refund.reason,
            status = refund.status,
            retryCount = refund.retryCount,
            nextRetryAt = refund.nextRetryAt,
            failureCode = refund.failureCode,
            failureMessage = refund.failureMessage,
            requestedAt = refund.requestedAt,
            refundedAt = refund.refundedAt,
        )
    }
}
