package com.gachisa.payment.client;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.client.dto.PgConfirmationResult;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.entity.PaymentMethod;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class PgClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String PAYMENT_PATH = "/v1/payments/{paymentKey}";
    private final RestClient restClient;
    private final String secretKey;

    @Autowired
    public PgClient(
            @Qualifier("tossRestClient") RestClient restClient,
            @Value("${payment.toss.secret-key:}") String secretKey
    ) {
        this.restClient = restClient;
        this.secretKey = secretKey;
    }

    public PgConfirmationResult confirm(String paymentKey, String pgOrderId, int amount,
                                        String pgIdempotencyKey, PaymentMethod requestedPaymentMethod) {
        validateSecretKey();

        try {
            TossPaymentResponse response = restClient.post()
                    .uri(CONFIRM_PATH)
                    .header("Idempotency-Key", pgIdempotencyKey)
                    .body(new TossPaymentConfirmRequest(paymentKey, pgOrderId, amount))
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null) {
                throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
            }

            return new PgConfirmationResult(
                    response.paymentKey(),
                    response.orderId(),
                    response.totalAmount(),
                    convertPaymentMethod(response.method(), requestedPaymentMethod)
            );
        } catch (ResourceAccessException exception) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    public PgCancellationResult cancel(String paymentKey, String cancelReason, String pgIdempotencyKey) {
        validateSecretKey();

        try {
            TossPaymentResponse response = restClient.post()
                    .uri(CANCEL_PATH, paymentKey)
                    .header("Idempotency-Key", pgIdempotencyKey)
                    .body(new TossPaymentCancelRequest(cancelReason))
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null || response.cancels() == null || response.cancels().isEmpty()) {
                throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
            }

            TossCancelResponse cancel = response.cancels().get(response.cancels().size() - 1);
            if (!"DONE".equals(cancel.cancelStatus())) {
                throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
            }

            return new PgCancellationResult(
                    response.paymentKey(),
                    response.orderId(),
                    cancel.transactionKey(),
                    cancel.cancelAmount()
            );
        } catch (ResourceAccessException exception) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    public PgPaymentQueryResult getPayment(String paymentKey) {
        validateSecretKey();

        try {
            TossPaymentResponse response = restClient.get()
                    .uri(PAYMENT_PATH, paymentKey)
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null) {
                throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
            }

            return new PgPaymentQueryResult(
                    response.paymentKey(),
                    response.orderId(),
                    response.totalAmount(),
                    response.status(),
                    convertPaymentMethod(response.method(), null),
                    getLastCancellationTransactionKey(response.cancels()),
                    getLastCancellationReason(response.cancels()),
                    getTotalCancelledAmount(response.cancels())
            );
        } catch (ResourceAccessException exception) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    private void validateSecretKey() {
        if (!StringUtils.hasText(secretKey)) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED);
        }
    }

    private PaymentMethod convertPaymentMethod(String method, PaymentMethod fallback) {
        if ("카드".equals(method)) {
            return PaymentMethod.CARD;
        }
        if ("간편결제".equals(method)) {
            return PaymentMethod.EASY_PAY;
        }
        return fallback;
    }

    private String getLastCancellationTransactionKey(List<TossCancelResponse> cancels) {
        return hasCancellations(cancels) ? cancels.get(cancels.size() - 1).transactionKey() : null;
    }

    private String getLastCancellationReason(List<TossCancelResponse> cancels) {
        return hasCancellations(cancels) ? cancels.get(cancels.size() - 1).cancelReason() : null;
    }

    private int getTotalCancelledAmount(List<TossCancelResponse> cancels) {
        return hasCancellations(cancels)
                ? cancels.stream().mapToInt(TossCancelResponse::cancelAmount).sum()
                : 0;
    }

    private boolean hasCancellations(List<TossCancelResponse> cancels) {
        return cancels != null && !cancels.isEmpty();
    }

    private record TossPaymentConfirmRequest(
            String paymentKey,
            String orderId,
            int amount
    ) {
    }

    private record TossPaymentCancelRequest(String cancelReason) {
    }

    private record TossPaymentResponse(
            String paymentKey,
            String orderId,
            int totalAmount,
            String status,
            String method,
            List<TossCancelResponse> cancels
    ) {
    }

    private record TossCancelResponse(
            int cancelAmount,
            String cancelReason,
            String transactionKey,
            String cancelStatus
    ) {
    }
}
