package com.gachisa.payment.dto

import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.entity.PaymentStatus
import java.time.LocalDateTime

data class PaymentResponse(
    val paymentId: Long,
    val paymentAttemptId: Long?,
    val participationId: Long,
    val orderId: Long?,
    val pgOrderId: String?,
    val pgPaymentKey: String?,
    val amount: Int,
    val paymentStatus: PaymentStatus,
    val attemptStatus: PaymentAttemptStatus?,
    val paymentMethod: PaymentMethod?,
    val retryCount: Int,
    val nextRetryAt: LocalDateTime?,
    val failureCode: String?,
    val failureMessage: String?,
    val expiresAt: LocalDateTime?,
    val paidAt: LocalDateTime?,
    val refundedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(payment: Payment, attempt: PaymentAttempt): PaymentResponse =
            from(payment, attempt, null)

        fun from(payment: Payment, attempt: PaymentAttempt?, orderId: Long?): PaymentResponse = PaymentResponse(
            paymentId = payment.id!!,
            paymentAttemptId = attempt?.id,
            participationId = payment.participationId,
            orderId = orderId,
            pgOrderId = attempt?.pgOrderId,
            pgPaymentKey = attempt?.pgPaymentKey,
            amount = payment.amount,
            paymentStatus = payment.status,
            attemptStatus = attempt?.status,
            paymentMethod = attempt?.paymentMethod,
            retryCount = attempt?.retryCount ?: 0,
            nextRetryAt = attempt?.nextRetryAt,
            failureCode = attempt?.failureCode,
            failureMessage = attempt?.failureMessage,
            expiresAt = attempt?.expiresAt,
            paidAt = payment.paidAt,
            refundedAt = payment.refundedAt,
            createdAt = payment.createdAt,
        )
    }
}
