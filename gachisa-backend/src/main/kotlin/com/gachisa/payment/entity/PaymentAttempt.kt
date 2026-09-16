package com.gachisa.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_attempt")
class PaymentAttempt(
    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false, unique = true, length = 36)
    val clientRequestId: String,

    @Column(nullable = false, unique = true, length = 36)
    val pgIdempotencyKey: String,

    @Column(nullable = false, unique = true)
    val pgOrderId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val paymentMethod: PaymentMethod,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentAttemptStatus,

    @Column(nullable = false)
    var retryCount: Int,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(nullable = false)
    var updatedAt: LocalDateTime,

    @Column(unique = true)
    var pgPaymentKey: String? = null,
    var nextRetryAt: LocalDateTime? = null,
    var failureCode: String? = null,
    var failureMessage: String? = null,
    var paidAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun beginConfirmation(pgPaymentKey: String, startedAt: LocalDateTime) {
        status = PaymentAttemptStatus.PROCESSING
        this.pgPaymentKey = pgPaymentKey
        updatedAt = startedAt
        nextRetryAt = startedAt.plusSeconds(10)
    }

    fun complete(paidAt: LocalDateTime) {
        status = PaymentAttemptStatus.PAID
        this.paidAt = paidAt
        updatedAt = paidAt
        failureCode = null
        failureMessage = null
        nextRetryAt = null
    }

    fun fail(failureCode: String, failureMessage: String, failedAt: LocalDateTime) {
        status = PaymentAttemptStatus.FAILED
        this.failureCode = failureCode
        this.failureMessage = failureMessage
        updatedAt = failedAt
        nextRetryAt = null
    }

    fun expire(expiredAt: LocalDateTime) {
        status = PaymentAttemptStatus.EXPIRED
        failureCode = "TOSS_PAYMENT_EXPIRED"
        failureMessage = "토스 결제 인증 또는 승인 시간이 만료되었습니다."
        updatedAt = expiredAt
        nextRetryAt = null
    }

    fun cancel(cancelledAt: LocalDateTime) {
        status = PaymentAttemptStatus.CANCELLED
        updatedAt = cancelledAt
        nextRetryAt = null
    }

    fun recordRecoveryAttempt(attemptedAt: LocalDateTime, nextRetryAt: LocalDateTime) {
        retryCount++
        updatedAt = attemptedAt
        this.nextRetryAt = nextRetryAt
    }

    fun recordRecoveryFailure(failureCode: String, failureMessage: String, failedAt: LocalDateTime) {
        this.failureCode = failureCode
        this.failureMessage = failureMessage
        updatedAt = failedAt
    }

    fun exhaustRetry(failureCode: String, failureMessage: String, exhaustedAt: LocalDateTime) {
        status = PaymentAttemptStatus.RETRY_EXHAUSTED
        this.failureCode = failureCode
        this.failureMessage = failureMessage
        nextRetryAt = null
        updatedAt = exhaustedAt
    }

    fun isExpired(now: LocalDateTime): Boolean = !now.isBefore(expiresAt)
}
