package com.gachisa.payment.client

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.config.TossRestClientConfig
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.client.dto.PgConfirmationResult
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.entity.PaymentMethod
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

class PgClientTest {

    private val BASE_URL: String = "https://api.tosspayments.com"
    private val PAYMENT_KEY: String = "payment-key"
    private val ORDER_ID: String = "gachisa_order"
    private val IDEMPOTENCY_KEY: String = "9ce93331-2eba-4c19-b378-8f9bd48a3714"

    private lateinit var server: MockRestServiceServer
    private lateinit var pgClient: PgClient

    @BeforeEach
    fun setUp() {
        val secretKey = "test_sk_secret"
        val builder = TossRestClientConfig()
                .configure(RestClient.builder(), BASE_URL, secretKey)
        server = MockRestServiceServer.bindTo(builder).build()
        pgClient = PgClient(builder.build(), secretKey)
    }

    @Test
    fun confirmMapsTossTotalAmountAndSendsIdempotencyKey() {
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
                        """, MediaType.APPLICATION_JSON))

        val result = pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        )

        assertThat(result.amount).isEqualTo(12_600)
        assertThat(result.paymentMethod).isEqualTo(PaymentMethod.CARD)
        server.verify()
    }

    @Test
    fun getPaymentQueriesTossAndMapsCancellationHistory() {
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
                        """, MediaType.APPLICATION_JSON))

        val result = pgClient.getPayment(PAYMENT_KEY)

        assertThat(result.status).isEqualTo("CANCELED")
        assertThat(result.cancelledAmount).isEqualTo(12_600)
        assertThat(result.cancellationTransactionKey).isEqualTo("cancel-transaction")
        server.verify()
    }

    @Test
    fun cancelSendsRefundIdempotencyKeyAndMapsCancellation() {
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
                        """, MediaType.APPLICATION_JSON))

        val result = pgClient.cancel(
                PAYMENT_KEY,
                "공동구매 목표 인원 미달",
                IDEMPOTENCY_KEY
        )

        assertThat(result.cancelledAmount).isEqualTo(12_600)
        assertThat(result.cancellationTransactionKey).isEqualTo("cancel-transaction")
        server.verify()
    }

    @Test
    fun confirmTreatsServerErrorAsUnknownResult() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        ) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        server.verify()
    }

    @Test
    fun confirmTreatsBadRequestAsDefiniteRejection() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertThatThrownBy { pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        ) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_REJECTED)
        server.verify()
    }

    @Test
    fun confirmKeepsProcessingWhenSameIdempotentRequestIsStillRunning() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.CONFLICT))

        assertThatThrownBy { pgClient.confirm(
                PAYMENT_KEY,
                ORDER_ID,
                12_600,
                IDEMPOTENCY_KEY,
                PaymentMethod.CARD
        ) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_PROCESSING)
        server.verify()
    }
}
