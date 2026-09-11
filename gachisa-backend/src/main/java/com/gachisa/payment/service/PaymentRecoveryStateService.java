package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.order.dto.OrderCreateCommand;
import com.gachisa.order.dto.OrderResponse;
import com.gachisa.order.service.OrderService;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.service.dto.RecoveryPreparation;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryStateService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ParticipationService participationService;
    private final RefundCompletionService refundCompletionService;
    private final TimeProvider timeProvider;
    private final PgRetryPolicy retryPolicy;
    private final OrderService orderService;

    @Transactional
    public RecoveryPreparation prepare(Long attemptId) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        PaymentAttempt attempt = target.attempt();
        if (attempt.getStatus() != PaymentAttemptStatus.PROCESSING) {
            return RecoveryPreparation.skip(target.payment(), attempt);
        }
        if (attempt.getPgPaymentKey() == null) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }

        if (retryPolicy.isExhausted(attempt.getRetryCount())) {
            attempt.exhaustRetry(
                    "PAYMENT_RECOVERY_EXHAUSTED",
                    "결제 상태 자동 확인 횟수를 초과했습니다.",
                    timeProvider.now()
            );
            return RecoveryPreparation.skip(target.payment(), attempt);
        }

        LocalDateTime now = timeProvider.now();
        int nextRetryCount = attempt.getRetryCount() + 2;
        attempt.recordRecoveryAttempt(
                now,
                retryPolicy.nextRetryAt(now, nextRetryCount)
        );
        return RecoveryPreparation.query(attempt);
    }

    @Transactional
    public PaymentResponse apply(Long attemptId, PgPaymentQueryResult result) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        Payment payment = target.payment();
        PaymentAttempt attempt = target.attempt();
        validatePayment(payment, attempt, result);

        Long orderId = null;
        switch (result.status()) {
            case "DONE" -> orderId = complete(payment, attempt);
            case "CANCELED" -> refundCompletionService.reconcileCancellation(
                    payment,
                    result.cancellationReason(),
                    result.cancellationTransactionKey(),
                    result.cancelledAmount()
            );
            case "ABORTED" -> attempt.fail(
                    "TOSS_PAYMENT_ABORTED", "토스 결제 승인이 실패했습니다.", timeProvider.now());
            case "EXPIRED" -> attempt.expire(timeProvider.now());
            default -> recordUnknownStatus(attempt, result.status());
        }
        return PaymentResponse.from(payment, attempt, orderId);
    }

    @Transactional
    public void recordFailure(Long attemptId, ErrorCode errorCode) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        PaymentAttempt attempt = target.attempt();
        if (attempt.getStatus() != PaymentAttemptStatus.PROCESSING) {
            return;
        }

        if (retryPolicy.isExhausted(attempt.getRetryCount())) {
            attempt.exhaustRetry(errorCode.name(), errorCode.getMessage(), timeProvider.now());
            return;
        }
        attempt.recordRecoveryFailure(errorCode.name(), errorCode.getMessage(), timeProvider.now());
    }

    @Transactional(readOnly = true)
    public Long findAttemptIdByPgOrderId(String pgOrderId) {
        return paymentAttemptRepository.findByPgOrderId(pgOrderId)
                .map(PaymentAttempt::getId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    }

    private Long complete(Payment payment, PaymentAttempt attempt) {
        if (attempt.getStatus() != PaymentAttemptStatus.PAID) {
            if (payment.getStatus() != PaymentStatus.READY
                    || attempt.getStatus() != PaymentAttemptStatus.PROCESSING) {
                throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
            participationService.confirmPayment(payment.getParticipationId());
            payment.complete(timeProvider.now());
            attempt.complete(timeProvider.now());
        }

        ParticipationPaymentInfo participation =
                participationService.getPaymentInfo(payment.getParticipationId());
        OrderResponse order = orderService.createOrderIfAbsent(new OrderCreateCommand(
                payment.getParticipationId(), payment.getId(), participation.userId(),
                participation.groupBuyId(), participation.quantity(), payment.getAmount()));
        return order.orderId();
    }

    private void validatePayment(Payment payment, PaymentAttempt attempt, PgPaymentQueryResult result) {
        if (!attempt.getPgOrderId().equals(result.pgOrderId())
                || payment.getAmount() != result.amount()
                || !attempt.getPgPaymentKey().equals(result.paymentKey())) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }
    }

    private void recordUnknownStatus(PaymentAttempt attempt, String status) {
        String message = "확인되지 않은 Toss 결제 상태입니다: " + status;
        if (retryPolicy.isExhausted(attempt.getRetryCount())) {
            attempt.exhaustRetry("TOSS_PAYMENT_STATUS_UNKNOWN", message, timeProvider.now());
            return;
        }
        attempt.recordRecoveryFailure("TOSS_PAYMENT_STATUS_UNKNOWN", message, timeProvider.now());
    }

    private PaymentAndAttempt getForUpdate(Long attemptId) {
        Long paymentId = paymentAttemptRepository.findPaymentIdByAttemptId(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
        return new PaymentAndAttempt(payment, attempt);
    }

    private record PaymentAndAttempt(Payment payment, PaymentAttempt attempt) {
    }

}
