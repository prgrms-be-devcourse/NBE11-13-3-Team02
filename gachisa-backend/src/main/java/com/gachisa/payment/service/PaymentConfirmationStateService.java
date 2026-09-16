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
import java.util.Optional;
import com.gachisa.payment.service.dto.ConfirmationPreparation;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
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
    private final OrderService orderService;

    /**
     * 1차 트랜잭션. 락을 잡고 확정 가능한 상태인지 검증만 하고 아무것도 바꾸지 않는다.
     *
     * <p>여기서 트랜잭션을 한 번 끊는 이유는 다음 단계인 대기열 확정 시작이 외부 호출이기
     * 때문이다. PESSIMISTIC_WRITE 락을 쥔 채 네트워크를 기다리면 결제가 몰릴 때 락 점유
     * 시간이 그대로 늘어난다. 대기열이 존재하는 이유인 폭주 상황에서 정확히 문제가 된다.
     *
     * @return 이미 결론이 난 건이면 그 응답. 비어 있으면 확정을 진행해도 된다.
     */
    @Transactional
    public Optional<PaymentResponse> checkConfirmable(Long attemptId, PaymentConfirmRequest request) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        validateRequest(target.payment(), target.attempt(), request);

        ConfirmationPreparation settled = settledOrNull(target, request);
        if (settled != null) {
            return Optional.of(settled.existingResponse());
        }
        requireReadyToConfirm(target);
        return Optional.empty();
    }

    /**
     * 2차 트랜잭션. 대기열 확정을 시작한 뒤 호출한다.
     *
     * <p>1차와 2차 사이에 락이 풀려 있으므로 다른 요청이 상태를 바꿨을 수 있다.
     * 락을 다시 잡고 같은 검증을 되풀이한다.
     */
    @Transactional
    public ConfirmationPreparation beginConfirmation(Long attemptId, PaymentConfirmRequest request) {
        PaymentAndAttempt target = getForUpdate(attemptId);
        validateRequest(target.payment(), target.attempt(), request);

        ConfirmationPreparation settled = settledOrNull(target, request);
        if (settled != null) {
            return settled;
        }
        requireReadyToConfirm(target);

        target.attempt().beginConfirmation(request.paymentKey(), timeProvider.now());
        return ConfirmationPreparation.request(target.payment(), target.attempt());
    }

    /** 이미 결론이 난 상태(PAID/PROCESSING)면 그 응답을, 아니면 null을 돌려준다. */
    private ConfirmationPreparation settledOrNull(PaymentAndAttempt target, PaymentConfirmRequest request) {
        Payment payment = target.payment();
        PaymentAttempt attempt = target.attempt();

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
        return null;
    }

    private void requireReadyToConfirm(PaymentAndAttempt target) {
        Payment payment = target.payment();
        PaymentAttempt attempt = target.attempt();

        if (payment.getStatus() != PaymentStatus.READY
                || attempt.getStatus() != PaymentAttemptStatus.READY) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (attempt.isExpired(timeProvider.now())) {
            attempt.expire(timeProvider.now());
            throw new CustomException(ErrorCode.PAYMENT_EXPIRED);
        }
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
