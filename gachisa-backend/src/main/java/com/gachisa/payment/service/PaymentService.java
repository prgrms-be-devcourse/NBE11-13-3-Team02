package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.order.service.OrderService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgConfirmationResult;
import com.gachisa.payment.service.dto.ConfirmationPreparation;
import com.gachisa.payment.dto.PaymentConfirmRequest;
import com.gachisa.payment.dto.PaymentRequest;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.payment.repository.PaymentAttemptRepository;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.queue.service.QueueService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(10);
    private static final String PG_ORDER_PREFIX = "gachisa_";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ParticipationService participationService;
    private final PaymentAmountCalculator amountCalculator;
    private final PgClient pgClient;
    private final PaymentConfirmationStateService confirmationStateService;
    private final TimeProvider timeProvider;
    private final QueueService queueService;
    private final OrderService orderService;

    @Transactional
    public PaymentResponse createPayment(Long participationId, Long userId, String clientRequestId,
                                         String queueToken, PaymentRequest request) {
        validateClientRequestId(clientRequestId);
        ParticipationPaymentInfo participation = participationService.getPaymentInfo(participationId);
        validateOwner(participation, userId);
        validatePayable(participation);

        PaymentAttempt idempotentAttempt = paymentAttemptRepository.findByClientRequestId(clientRequestId).orElse(null);
        if (idempotentAttempt != null) {
            Payment payment = getPayment(idempotentAttempt.getPaymentId());
            validateSamePaymentRequest(payment, idempotentAttempt, participationId, request);
            return PaymentResponse.from(payment, idempotentAttempt);
        }

        queueService.requireAdmission(participation.groupBuyId(), userId, queueToken);

        Payment existingPayment = paymentRepository.findByParticipationId(participationId).orElse(null);
        LocalDateTime now = timeProvider.now();
        if (existingPayment == null) {
            paymentRepository.insertReadyIfAbsent(
                    participationId,
                    amountCalculator.calculate(participation),
                    now
            );
        }

        Payment payment = paymentRepository.findByParticipationIdForUpdate(participationId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        PaymentAttempt concurrentlyCreatedAttempt = paymentAttemptRepository
                .findByClientRequestId(clientRequestId)
                .orElse(null);
        if (concurrentlyCreatedAttempt != null) {
            validateSamePaymentRequest(payment, concurrentlyCreatedAttempt, participationId, request);
            return PaymentResponse.from(payment, concurrentlyCreatedAttempt);
        }

        validateNewAttemptAllowed(payment);
        PaymentAttempt activeAttempt = paymentAttemptRepository
                .findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
                        payment.getId(),
                        List.of(PaymentAttemptStatus.READY, PaymentAttemptStatus.PROCESSING)
                )
                .orElse(null);
        if (activeAttempt != null) {
            if (activeAttempt.getStatus() == PaymentAttemptStatus.PROCESSING) {
                throw new CustomException(ErrorCode.PAYMENT_ATTEMPT_IN_PROGRESS);
            }
            if (activeAttempt.getPaymentMethod() == request.paymentMethod()) {
                return PaymentResponse.from(payment, activeAttempt);
            }
            activeAttempt.cancel(now);
        }

        PaymentAttempt attempt = PaymentAttempt.builder()
                .paymentId(payment.getId())
                .clientRequestId(clientRequestId)
                .pgIdempotencyKey(UUID.randomUUID().toString())
                .pgOrderId(createPgOrderId())
                .paymentMethod(request.paymentMethod())
                .status(PaymentAttemptStatus.READY)
                .retryCount(0)
                .expiresAt(now.plus(PAYMENT_TIMEOUT))
                .createdAt(now)
                .updatedAt(now)
                .build();
        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);
        queueService.bindPaymentAttempt(participation.groupBuyId(), userId, savedAttempt.getId());
        return PaymentResponse.from(payment, savedAttempt);
    }

    public PaymentResponse confirmPayment(Long paymentAttemptId, Long userId, PaymentConfirmRequest request) {
        PaymentAttempt attempt = getPaymentAttempt(paymentAttemptId);
        Payment payment = getPayment(attempt.getPaymentId());
        ParticipationPaymentInfo participation = participationService.getPaymentInfo(payment.getParticipationId());
        validateOwner(participation, userId);

        ConfirmationPreparation preparation = confirmationStateService.prepare(paymentAttemptId, request);
        if (!preparation.requestRequired()) {
            if (preparation.existingResponse().paymentStatus() == PaymentStatus.PAID) {
                queueService.completeAdmission(participation.groupBuyId(), userId);
            }
            return preparation.existingResponse();
        }

        try {
            PgConfirmationResult result = pgClient.confirm(
                    preparation.paymentKey(),
                    preparation.pgOrderId(),
                    preparation.amount(),
                    preparation.pgIdempotencyKey(),
                    preparation.paymentMethod()
            );
            PaymentResponse response = confirmationStateService.complete(paymentAttemptId, result);
            queueService.completeAdmission(participation.groupBuyId(), userId);
            return response;
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.PAYMENT_GATEWAY_REJECTED) {
                confirmationStateService.fail(paymentAttemptId, exception.getErrorCode());
                queueService.confirmationFailed(participation.groupBuyId(), userId);
            } else {
                confirmationStateService.keepProcessing(paymentAttemptId, exception.getErrorCode());
            }
            throw exception;
        }
    }

    public PaymentResponse confirmPaymentByPgOrderId(Long userId, PaymentConfirmRequest request) {
        Long paymentAttemptId = paymentAttemptRepository.findByPgOrderId(request.pgOrderId())
                .map(PaymentAttempt::getId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
        return confirmPayment(paymentAttemptId, userId, request);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, Long userId) {
        Payment payment = getPayment(paymentId);
        ParticipationPaymentInfo participation = participationService.getPaymentInfo(payment.getParticipationId());
        validateOwner(participation, userId);
        PaymentAttempt attempt = paymentAttemptRepository.findFirstByPaymentIdOrderByCreatedAtDesc(paymentId)
                .orElse(null);
        Long orderId = payment.getStatus() == PaymentStatus.PAID
                ? orderService.getOrderIdByParticipationId(payment.getParticipationId())
                : null;
        return PaymentResponse.from(payment, attempt, orderId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByPgOrderId(String pgOrderId, Long userId) {
        PaymentAttempt attempt = paymentAttemptRepository.findByPgOrderId(pgOrderId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
        Payment payment = getPayment(attempt.getPaymentId());
        ParticipationPaymentInfo participation = participationService.getPaymentInfo(payment.getParticipationId());
        validateOwner(participation, userId);
        Long orderId = payment.getStatus() == PaymentStatus.PAID
                ? orderService.getOrderIdByParticipationId(payment.getParticipationId())
                : null;
        return PaymentResponse.from(payment, attempt, orderId);
    }

    private Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentAttempt getPaymentAttempt(Long paymentAttemptId) {
        return paymentAttemptRepository.findById(paymentAttemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    }

    private void validateOwner(ParticipationPaymentInfo participation, Long userId) {
        if (!participation.userId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePayable(ParticipationPaymentInfo participation) {
        if (!participation.payable()) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
    }

    private void validateClientRequestId(String clientRequestId) {
        try {
            UUID uuid = UUID.fromString(clientRequestId);
            if (uuid.version() != 4 || !uuid.toString().equalsIgnoreCase(clientRequestId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_INVALID);
        }
    }

    private void validateSamePaymentRequest(Payment payment, PaymentAttempt attempt,
                                            Long participationId, PaymentRequest request) {
        if (!payment.getParticipationId().equals(participationId)
                || attempt.getPaymentMethod() != request.paymentMethod()) {
            throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT);
        }
    }

    private void validateNewAttemptAllowed(Payment payment) {
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
    }

    private String createPgOrderId() {
        return PG_ORDER_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

}
