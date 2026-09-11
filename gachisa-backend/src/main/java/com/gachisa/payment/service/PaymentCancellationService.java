package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.dto.PaymentCancellationResponse;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.RefundRepository;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.queue.service.QueueService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCancellationService {

    private static final String USER_CANCELLATION_REASON = "구매자 공동구매 참여 취소";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final ParticipationService participationService;
    private final RefundService refundService;
    private final QueueService queueService;
    private final TimeProvider timeProvider;

    @Transactional
    public PaymentCancellationResponse cancel(Long participationId, Long userId) {
        ParticipationPaymentInfo participation = participationService.getPaymentInfo(participationId);
        if (!participation.userId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Payment payment = paymentRepository.findByParticipationIdForUpdate(participationId).orElse(null);
        if (payment == null || payment.getStatus() == PaymentStatus.READY) {
            if (payment != null) {
                PaymentAttempt activeAttempt = paymentAttemptRepository
                        .findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
                                payment.getId(),
                                List.of(PaymentAttemptStatus.READY, PaymentAttemptStatus.PROCESSING))
                        .orElse(null);
                if (activeAttempt != null && activeAttempt.getStatus() == PaymentAttemptStatus.READY) {
                    activeAttempt.cancel(timeProvider.now());
                }
            }
            participationService.cancel(participationId, userId);
            queueService.completeAdmission(participation.groupBuyId(), userId);
            return PaymentCancellationResponse.cancelled();
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentCancellationResponse.refundRequested(
                    refundService.requestRefund(payment.getId(), USER_CANCELLATION_REASON));
        }
        RefundResponse refund = getRefund(payment.getId());
        return PaymentCancellationResponse.refundRequested(refund);
    }

    public RefundResponse getRefundStatus(Long participationId, Long userId) {
        ParticipationPaymentInfo participation = participationService.getPaymentInfo(participationId);
        if (!participation.userId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        Payment payment = paymentRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        return getRefund(payment.getId());
    }

    private RefundResponse getRefund(Long paymentId) {
        return refundRepository.findByPaymentId(paymentId)
                .map(RefundResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.REFUND_NOT_FOUND));
    }
}
