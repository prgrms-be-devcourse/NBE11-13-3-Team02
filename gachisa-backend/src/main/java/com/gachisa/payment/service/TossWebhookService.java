package com.gachisa.payment.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.PgClient;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.dto.TossPaymentWebhookRequest;
import com.gachisa.payment.dto.TossWebhookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TossWebhookService {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    private final PgClient pgClient;
    private final TossWebhookStateService webhookStateService;

    public TossWebhookResponse process(String transmissionId, TossPaymentWebhookRequest request) {
        if (!StringUtils.hasText(transmissionId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (!PAYMENT_STATUS_CHANGED.equals(request.eventType())) {
            return new TossWebhookResponse(false);
        }

        PgPaymentQueryResult pgPayment = pgClient.getPayment(request.data().paymentKey());
        validateWebhookBody(request, pgPayment);
        return new TossWebhookResponse(
                webhookStateService.apply(transmissionId, request.eventType(), pgPayment)
        );
    }

    private void validateWebhookBody(TossPaymentWebhookRequest request, PgPaymentQueryResult pgPayment) {
        if (!request.data().paymentKey().equals(pgPayment.paymentKey())
                || !request.data().orderId().equals(pgPayment.pgOrderId())
                || !request.data().status().equals(pgPayment.status())) {
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE);
        }
    }
}
