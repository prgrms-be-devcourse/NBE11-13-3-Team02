package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.order.dto.OrderCreateCommand;
import com.gachisa.order.dto.OrderResponse;
import com.gachisa.order.service.OrderService;
import com.gachisa.payment.client.dto.PgConfirmationResult;
import com.gachisa.payment.dto.PaymentConfirmRequest;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.service.dto.ConfirmationPreparation;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentConfirmationStateService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ParticipationService participationService;
    private final TimeProvider timeProvider;
    private final QueueService queueService;
    private final OrderService orderService;

    @Transactional
    public ConfirmationPreparation prepare(Long attemptId, PaymentConfirmRequest request) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        Payment payment = target.payment();
        PaymentAttempt attempt = target.attempt();
        validateRequest(payment, attempt, request);

        if (attempt.getStatus() == PaymentAttemptStatus.PAID) {
            ParticipationPaymentInfo participation =
                    participationService.getPaymentInfo(payment.getParticipationId());
            OrderResponse order = createOrder(payment, participation);
            return ConfirmationPreparation.existing(payment, attempt, order.orderId());
        }
        if (attempt.getStatus() == PaymentAttemptStatus.PROCESSING) {
            if (!request.paymentKey().equals(attempt.getPgPaymentKey())) {
                throw new CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
            }
            return ConfirmationPreparation.existing(payment, attempt);
        }
        if (payment.getStatus() != PaymentStatus.READY
                || attempt.getStatus() != PaymentAttemptStatus.READY) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (attempt.isExpired(timeProvider.now())) {
            attempt.expire(timeProvider.now());
            throw new CustomException(ErrorCode.PAYMENT_EXPIRED);
        }

        ParticipationPaymentInfo participation = participationService.getPaymentInfo(payment.getParticipationId());
        queueService.startConfirmation(participation.groupBuyId(), participation.userId());
        attempt.beginConfirmation(request.paymentKey(), timeProvider.now());
        return ConfirmationPreparation.request(payment, attempt);
    }

    @Transactional
    public PaymentResponse complete(Long attemptId, PgConfirmationResult result) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        Payment payment = target.payment();
        PaymentAttempt attempt = target.attempt();
        if (attempt.getStatus() == PaymentAttemptStatus.PAID) {
            ParticipationPaymentInfo participation =
                    participationService.getPaymentInfo(payment.getParticipationId());
            OrderResponse order = createOrder(payment, participation);
            return PaymentResponse.from(payment, attempt, order.orderId());
        }
        if (payment.getStatus() != PaymentStatus.READY
                || attempt.getStatus() != PaymentAttemptStatus.PROCESSING
                || !attempt.getPgOrderId().equals(result.pgOrderId())
                || payment.getAmount() != result.amount()
                || !attempt.getPgPaymentKey().equals(result.pgTransactionId())) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }

        ParticipationPaymentInfo participation =
                participationService.getPaymentInfo(payment.getParticipationId());
        participationService.confirmPayment(payment.getParticipationId());
        payment.complete(timeProvider.now());
        attempt.complete(timeProvider.now());
        OrderResponse order = createOrder(payment, participation);
        return PaymentResponse.from(payment, attempt, order.orderId());
    }

    private OrderResponse createOrder(Payment payment, ParticipationPaymentInfo participation) {
        return orderService.createOrderIfAbsent(new OrderCreateCommand(
                payment.getParticipationId(), payment.getId(), participation.userId(),
                participation.groupBuyId(), participation.quantity(), payment.getAmount()));
    }

    @Transactional
    public void fail(Long attemptId, ErrorCode errorCode) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        if (target.attempt().getStatus() == PaymentAttemptStatus.PROCESSING) {
            target.attempt().fail(errorCode.name(), errorCode.getMessage(), timeProvider.now());
        }
    }

    @Transactional
    public void keepProcessing(Long attemptId, ErrorCode errorCode) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        if (target.attempt().getStatus() == PaymentAttemptStatus.PROCESSING) {
            target.attempt().recordRecoveryFailure(
                    errorCode.name(), errorCode.getMessage(), timeProvider.now());
        }
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

    private void validateRequest(Payment payment, PaymentAttempt attempt, PaymentConfirmRequest request) {
        if (!attempt.getPgOrderId().equals(request.pgOrderId())) {
            throw new CustomException(ErrorCode.PAYMENT_ORDER_MISMATCH);
        }
        if (payment.getAmount() != request.amount()) {
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private record PaymentAndAttempt(Payment payment, PaymentAttempt attempt) {
    }

}
