package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.order.service.OrderService;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.repository.RefundRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RefundCompletionService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final ParticipationService participationService;
    private final TimeProvider timeProvider;
    private final OrderService orderService;

    @Transactional
    public RefundResponse complete(Long refundId, PgCancellationResult result) {
        Refund refundSnapshot = getRefund(refundId);
        Payment payment = paymentRepository.findByIdForUpdate(refundSnapshot.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));

        if (refund.getStatus() == RefundStatus.REFUNDED) {
            return RefundResponse.from(refund);
        }

        PaymentAttempt paidAttempt = getPaidAttempt(payment.getId());
        validateCancellation(paidAttempt, refund, result);

        LocalDateTime now = timeProvider.now();
        payment.refund(now);
        refund.complete(result.cancellationTransactionKey(), now);
        synchronizeParticipationRefund(payment.getParticipationId());
        orderService.reflectRefund(payment.getId());
        return RefundResponse.from(refund);
    }

    @Transactional
    public void reconcileCancellation(Payment payment, String reason,
                                      String transactionId, int cancelledAmount) {
        if (cancelledAmount != payment.getAmount()) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }

        Refund refund = refundRepository.findByPaymentIdForUpdate(payment.getId()).orElse(null);
        LocalDateTime now = timeProvider.now();
        if (refund == null) {
            refund = createRefund(payment, reason, now);
        }
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            refund.complete(transactionId, now);
        }
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            payment.refund(now);
        }
        synchronizeParticipationRefund(payment.getParticipationId());
        orderService.reflectRefund(payment.getId());
    }

    private Refund createRefund(Payment payment, String reason, LocalDateTime now) {
        String refundReason = StringUtils.hasText(reason)
                ? reason
                : "토스 결제 취소 상태 동기화";

        return refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .reason(refundReason)
                .status(RefundStatus.REFUND_PENDING)
                .pgIdempotencyKey(UUID.randomUUID().toString())
                .requestedAt(now)
                .updatedAt(now)
                .build());
    }

    private void synchronizeParticipationRefund(Long participationId) {
        if (participationService.getPaymentInfo(participationId).payable()) {
            participationService.confirmPayment(participationId);
        }
        participationService.refundPayment(participationId);
    }

    private Refund getRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
    }

    private PaymentAttempt getPaidAttempt(Long paymentId) {
        return paymentAttemptRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
                        paymentId, PaymentAttemptStatus.PAID)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    }

    private void validateCancellation(PaymentAttempt attempt, Refund refund,
                                      PgCancellationResult result) {
        if (!attempt.getPgPaymentKey().equals(result.paymentKey())
                || !attempt.getPgOrderId().equals(result.pgOrderId())
                || refund.getAmount() != result.cancelledAmount()) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }
    }
}
