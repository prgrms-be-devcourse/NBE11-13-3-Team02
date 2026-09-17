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
@Table(name = "refund")
class Refund(
    @Column(nullable = false, unique = true)
    val paymentId: Long,

    @Column(nullable = false)
    val amount: Int,

    @Column(nullable = false, length = 200)
    val reason: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefundStatus,

    @Column(nullable = false, unique = true, length = 36)
    val pgIdempotencyKey: String,

    @Column(nullable = false, updatable = false)
    val requestedAt: LocalDateTime,

    @Column(nullable = false)
    var updatedAt: LocalDateTime,

    var pgCancellationTransactionId: String? = null,
    var failureCode: String? = null,
    var failureMessage: String? = null,

    @Column(nullable = false)
    var retryCount: Int = 0,

    var nextRetryAt: LocalDateTime? = requestedAt,
    var refundedAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun complete(pgCancellationTransactionId: String?, refundedAt: LocalDateTime) {
        status = RefundStatus.REFUNDED
        this.pgCancellationTransactionId = pgCancellationTransactionId
        this.refundedAt = refundedAt
        updatedAt = refundedAt
        nextRetryAt = null
        failureCode = null
        failureMessage = null
    }

    fun fail(failureCode: String, failureMessage: String, failedAt: LocalDateTime) {
        status = RefundStatus.FAILED
        this.failureCode = failureCode
        this.failureMessage = failureMessage
        updatedAt = failedAt
        nextRetryAt = null
    }

    fun startProcessing(startedAt: LocalDateTime) {
        status = RefundStatus.PROCESSING
        updatedAt = startedAt
        failureCode = null
        failureMessage = null
        nextRetryAt = startedAt.plusSeconds(10)
    }

    fun scheduleRetry(failureCode: String, failureMessage: String, checkedAt: LocalDateTime, nextRetryAt: LocalDateTime) {
        status = RefundStatus.REFUND_PENDING
        this.failureCode = failureCode
        this.failureMessage = failureMessage
        updatedAt = checkedAt
        retryCount++
        this.nextRetryAt = nextRetryAt
    }

    fun retry(retriedAt: LocalDateTime) {
        status = RefundStatus.REFUND_PENDING
        updatedAt = retriedAt
        failureCode = null
        failureMessage = null
        retryCount = 0
        nextRetryAt = retriedAt
    }

    fun resume(retriedAt: LocalDateTime) {
        status = RefundStatus.REFUND_PENDING
        updatedAt = retriedAt
        nextRetryAt = retriedAt
    }

    fun exhaustRetry(failureCode: String, failureMessage: String, exhaustedAt: LocalDateTime) {
        status = RefundStatus.RETRY_EXHAUSTED
        this.failureCode = failureCode
        this.failureMessage = failureMessage
        updatedAt = exhaustedAt
        nextRetryAt = null
    }
}
