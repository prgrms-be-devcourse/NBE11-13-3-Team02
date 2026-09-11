package com.gachisa.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refund")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long paymentId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(nullable = false, unique = true, length = 36)
    private String pgIdempotencyKey;

    private String pgCancellationTransactionId;

    private String failureCode;

    private String failureMessage;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime nextRetryAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime refundedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Refund(Long paymentId, int amount, String reason, RefundStatus status,
                   String pgIdempotencyKey, LocalDateTime requestedAt, LocalDateTime updatedAt) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.pgIdempotencyKey = pgIdempotencyKey;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
        this.retryCount = 0;
        this.nextRetryAt = requestedAt;
    }

    public void complete(String pgCancellationTransactionId, LocalDateTime refundedAt) {
        this.status = RefundStatus.REFUNDED;
        this.pgCancellationTransactionId = pgCancellationTransactionId;
        this.refundedAt = refundedAt;
        this.updatedAt = refundedAt;
        this.nextRetryAt = null;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void fail(String failureCode, String failureMessage, LocalDateTime failedAt) {
        this.status = RefundStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = failedAt;
        this.nextRetryAt = null;
    }

    public void startProcessing(LocalDateTime startedAt) {
        this.status = RefundStatus.PROCESSING;
        this.updatedAt = startedAt;
        this.failureCode = null;
        this.failureMessage = null;
        this.nextRetryAt = startedAt.plusSeconds(10);
    }

    public void scheduleRetry(String failureCode, String failureMessage,
                              LocalDateTime checkedAt, LocalDateTime nextRetryAt) {
        this.status = RefundStatus.REFUND_PENDING;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = checkedAt;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
    }

    public void retry(LocalDateTime retriedAt) {
        this.status = RefundStatus.REFUND_PENDING;
        this.updatedAt = retriedAt;
        this.failureCode = null;
        this.failureMessage = null;
        this.retryCount = 0;
        this.nextRetryAt = retriedAt;
    }

    public void resume(LocalDateTime retriedAt) {
        this.status = RefundStatus.REFUND_PENDING;
        this.updatedAt = retriedAt;
        this.nextRetryAt = retriedAt;
    }

    public void exhaustRetry(String failureCode, String failureMessage, LocalDateTime exhaustedAt) {
        this.status = RefundStatus.RETRY_EXHAUSTED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = exhaustedAt;
        this.nextRetryAt = null;
    }
}
