package com.gachisa.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.client.config.TossRestClientConfig;
import com.gachisa.payment.client.dto.PgCancellationResult;
import com.gachisa.payment.client.dto.PgConfirmationResult;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.entity.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PgClientTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String PAYMENT_KEY = "payment-key";
    private static final String ORDER_ID = "gachisa_order";
    private static final String IDEMPOTENCY_KEY = "9ce93331-2eba-4c19-b378-8f9bd48a3714";

    private MockRestServiceServer server;
    private PgClient pgClient;

    @BeforeEach
    void setUp() {
        String secretKey = "test_sk_secret";
        RestClient.Builder builder = new TossRestClientConfig()
                .configure(RestClient.builder(), BASE_URL, secretKey);
        server = MockRestServiceServer.bindTo(builder).build();
        pgClient = new PgClient(builder.build(), secretKey);
    }

    @Test
    void confirmMapsTossTotalAmountAndSendsIdempotencyKey() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "payment-key",
                          "orderId": "gachisa_order",
                          "totalAmount": 12600,
                          "status": "DONE",
                          "method": "카드"
                        }
                        """, MediaType.APPLICATION_JSON));

        PgConfirmationResult result = pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        );

        assertThat(result.amount()).isEqualTo(12_600);
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        server.verify();
    }

    @Test
    void getPaymentQueriesTossAndMapsCancellationHistory() {
        server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "payment-key",
                          "orderId": "gachisa_order",
                          "totalAmount": 12600,
                          "status": "CANCELED",
                          "method": "카드",
                          "cancels": [{
                            "cancelAmount": 12600,
                            "cancelReason": "공동구매 목표 인원 미달",
                            "transactionKey": "cancel-transaction",
                            "cancelStatus": "DONE"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        PgPaymentQueryResult result = pgClient.getPayment(PAYMENT_KEY);

        assertThat(result.status()).isEqualTo("CANCELED");
        assertThat(result.cancelledAmount()).isEqualTo(12_600);
        assertThat(result.cancellationTransactionKey()).isEqualTo("cancel-transaction");
        server.verify();
    }

    @Test
    void cancelSendsRefundIdempotencyKeyAndMapsCancellation() {
        server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "payment-key",
                          "orderId": "gachisa_order",
                          "totalAmount": 12600,
                          "status": "CANCELED",
                          "method": "카드",
                          "cancels": [{
                            "cancelAmount": 12600,
                            "cancelReason": "공동구매 목표 인원 미달",
                            "transactionKey": "cancel-transaction",
                            "cancelStatus": "DONE"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        PgCancellationResult result = pgClient.cancel(
                PAYMENT_KEY,
                "공동구매 목표 인원 미달",
                IDEMPOTENCY_KEY
        );

        assertThat(result.cancelledAmount()).isEqualTo(12_600);
        assertThat(result.cancellationTransactionKey()).isEqualTo("cancel-transaction");
        server.verify();
    }

    @Test
    void confirmTreatsServerErrorAsUnknownResult() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        server.verify();
    }

    @Test
    void confirmTreatsBadRequestAsDefiniteRejection() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_REJECTED);
        server.verify();
    }

    @Test
    void confirmKeepsProcessingWhenSameIdempotentRequestIsStillRunning() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_PROCESSING);
        server.verify();
    }
}
