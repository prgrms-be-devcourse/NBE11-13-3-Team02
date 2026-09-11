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
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, unique = true, length = 36)
    private String clientRequestId;

    @Column(nullable = false, unique = true, length = 36)
    private String pgIdempotencyKey;

    @Column(nullable = false, unique = true)
    private String pgOrderId;

    @Column(unique = true)
    private String pgPaymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime nextRetryAt;

    private String failureCode;

    private String failureMessage;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime paidAt;

    @Builder
    private PaymentAttempt(Long paymentId, String clientRequestId, String pgIdempotencyKey, String pgOrderId, PaymentMethod paymentMethod,
                           PaymentAttemptStatus status, int retryCount, LocalDateTime expiresAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.paymentId = paymentId;
        this.clientRequestId = clientRequestId;
        this.pgIdempotencyKey = pgIdempotencyKey;
        this.pgOrderId = pgOrderId;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.retryCount = retryCount;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void beginConfirmation(String pgPaymentKey, LocalDateTime startedAt) {
        this.status = PaymentAttemptStatus.PROCESSING;
        this.pgPaymentKey = pgPaymentKey;
        this.updatedAt = startedAt;
        this.nextRetryAt = startedAt.plusSeconds(10);
    }

    public void complete(LocalDateTime paidAt) {
        this.status = PaymentAttemptStatus.PAID;
        this.paidAt = paidAt;
        this.updatedAt = paidAt;
        this.failureCode = null;
        this.failureMessage = null;
        this.nextRetryAt = null;
    }

    public void fail(String failureCode, String failureMessage, LocalDateTime failedAt) {
        this.status = PaymentAttemptStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = failedAt;
        this.nextRetryAt = null;
    }

    public void expire(LocalDateTime expiredAt) {
        this.status = PaymentAttemptStatus.EXPIRED;
        this.failureCode = "TOSS_PAYMENT_EXPIRED";
        this.failureMessage = "토스 결제 인증 또는 승인 시간이 만료되었습니다.";
        this.updatedAt = expiredAt;
        this.nextRetryAt = null;
    }

    public void cancel(LocalDateTime cancelledAt) {
        this.status = PaymentAttemptStatus.CANCELLED;
        this.updatedAt = cancelledAt;
        this.nextRetryAt = null;
    }

    public void recordRecoveryAttempt(LocalDateTime attemptedAt, LocalDateTime nextRetryAt) {
        this.retryCount++;
        this.updatedAt = attemptedAt;
        this.nextRetryAt = nextRetryAt;
    }

    public void recordRecoveryFailure(String failureCode, String failureMessage,
                                      LocalDateTime failedAt) {
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = failedAt;
    }

    public void exhaustRetry(String failureCode, String failureMessage, LocalDateTime exhaustedAt) {
        this.status = PaymentAttemptStatus.RETRY_EXHAUSTED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.nextRetryAt = null;
        this.updatedAt = exhaustedAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
