package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.RefundRepository;
import com.gachisa.payment.service.dto.RefundPreparation;
import com.gachisa.payment.service.dto.RefundRecoveryTarget;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RefundStateService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final TimeProvider timeProvider;
    private final PgRetryPolicy retryPolicy;

    @Transactional
    public RefundPreparation prepare(Long paymentId, String reason) {
        validateReason(reason);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        Refund existingRefund = refundRepository.findByPaymentIdForUpdate(paymentId).orElse(null);

        if (existingRefund != null) {
            if (existingRefund.getStatus() == RefundStatus.FAILED) {
                existingRefund.retry(timeProvider.now());
                return RefundPreparation.request(existingRefund, getPaidAttempt(payment.getId()));
            }
            return RefundPreparation.existing(existingRefund);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new CustomException(ErrorCode.REFUND_NOT_ALLOWED);
        }
        PaymentAttempt paidAttempt = getPaidAttempt(payment.getId());

        LocalDateTime now = timeProvider.now();
        Refund refund = Refund.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .reason(reason)
                .status(RefundStatus.REFUND_PENDING)
                .pgIdempotencyKey(UUID.randomUUID().toString())
                .requestedAt(now)
                .updatedAt(now)
                .build();

        return RefundPreparation.request(refundRepository.saveAndFlush(refund), paidAttempt);
    }

    @Transactional
    public RefundPreparation claimPending(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
        if (refund.getStatus() != RefundStatus.REFUND_PENDING) {
            return RefundPreparation.existing(refund);
        }

        PaymentAttempt paidAttempt = getPaidAttempt(refund.getPaymentId());
        refund.startProcessing(timeProvider.now());
        return RefundPreparation.request(refund, paidAttempt);
    }

    @Transactional
    public void keepPending(Long refundId, ErrorCode errorCode) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            if (retryPolicy.isExhausted(refund.getRetryCount())) {
                refund.exhaustRetry(errorCode.name(), errorCode.getMessage(), timeProvider.now());
                return;
            }

            LocalDateTime now = timeProvider.now();
            int nextRetryCount = refund.getRetryCount() + 1;
            refund.scheduleRetry(
                    errorCode.name(),
                    errorCode.getMessage(),
                    now,
                    retryPolicy.nextRetryAt(now, nextRetryCount)
            );
        }
    }

    @Transactional
    public void retryPending(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            refund.resume(timeProvider.now());
        }
    }

    @Transactional(readOnly = true)
    public RefundRecoveryTarget getRecoveryTarget(Long refundId) {
        Refund refund = getRefundEntity(refundId);
        PaymentAttempt paidAttempt = getPaidAttempt(refund.getPaymentId());
        return new RefundRecoveryTarget(
                refund.getId(),
                paidAttempt.getPgPaymentKey(),
                paidAttempt.getPgOrderId(),
                refund.getAmount()
        );
    }

    @Transactional
    public void fail(Long refundId, ErrorCode errorCode) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            refund.fail(errorCode.name(), errorCode.getMessage(), timeProvider.now());
        }
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long refundId) {
        return RefundResponse.from(getRefundEntity(refundId));
    }

    private Refund getRefundEntity(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
    }

    private void validateReason(String reason) {
        if (!StringUtils.hasText(reason) || reason.length() > 200) {
            throw new CustomException(ErrorCode.REFUND_REASON_REQUIRED);
        }
    }

    private PaymentAttempt getPaidAttempt(Long paymentId) {
        return paymentAttemptRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
                        paymentId, PaymentAttemptStatus.PAID)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    }

}
