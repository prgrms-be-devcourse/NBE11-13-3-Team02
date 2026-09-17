package com.gachisa.payment.client

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.client.dto.PgConfirmationResult
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.entity.PaymentMethod
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.util.concurrent.ConcurrentHashMap

@Component
class PgClient(
    @Qualifier("tossRestClient") private val restClient: RestClient,
    @Value("\${payment.toss.secret-key:}") private val secretKey: String,
    @Value("\${payment.toss.local-demo-enabled:false}") private val localDemoEnabled: Boolean = false,
) {
    private val localDemoPayments = ConcurrentHashMap<String, LocalDemoPayment>()

    fun confirm(paymentKey: String, pgOrderId: String, amount: Int, pgIdempotencyKey: String, requestedPaymentMethod: PaymentMethod): PgConfirmationResult {
        if (isLocalDemoPayment(paymentKey)) {
            localDemoPayments[paymentKey] = LocalDemoPayment(paymentKey, pgOrderId, amount, requestedPaymentMethod, "DONE", null, null)
            return PgConfirmationResult(paymentKey, pgOrderId, amount, requestedPaymentMethod)
        }
        validateSecretKey()
        try {
            val response = restClient.post().uri(CONFIRM_PATH).header("Idempotency-Key", pgIdempotencyKey)
                .body(TossPaymentConfirmRequest(paymentKey, pgOrderId, amount)).retrieve().body(TossPaymentResponse::class.java)
                ?: throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            return PgConfirmationResult(response.paymentKey, response.orderId, response.totalAmount, convertPaymentMethod(response.method, requestedPaymentMethod))
        } catch (exception: ResourceAccessException) {
            throw CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        }
    }

    fun cancel(paymentKey: String, cancelReason: String, pgIdempotencyKey: String): PgCancellationResult {
        if (isLocalDemoPayment(paymentKey)) {
            val payment = localDemoPayments[paymentKey]
                ?: throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            val transactionKey = "local-cancel-" + paymentKey.removePrefix(LOCAL_DEMO_PAYMENT_KEY_PREFIX)
            localDemoPayments[paymentKey] = payment.copy(
                status = "CANCELED",
                cancellationTransactionKey = transactionKey,
                cancellationReason = cancelReason,
            )
            return PgCancellationResult(paymentKey, payment.pgOrderId, transactionKey, payment.amount)
        }
        validateSecretKey()
        try {
            val response = restClient.post().uri(CANCEL_PATH, paymentKey).header("Idempotency-Key", pgIdempotencyKey)
                .body(TossPaymentCancelRequest(cancelReason)).retrieve().body(TossPaymentResponse::class.java)
                ?: throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            val cancel = response.cancels?.lastOrNull() ?: throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            if (cancel.cancelStatus != "DONE") throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            return PgCancellationResult(response.paymentKey, response.orderId, cancel.transactionKey, cancel.cancelAmount)
        } catch (exception: ResourceAccessException) {
            throw CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        }
    }

    fun getPayment(paymentKey: String): PgPaymentQueryResult {
        if (isLocalDemoPayment(paymentKey)) {
            val payment = localDemoPayments[paymentKey]
                ?: throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            return PgPaymentQueryResult(
                payment.paymentKey,
                payment.pgOrderId,
                payment.amount,
                payment.status,
                payment.paymentMethod,
                payment.cancellationTransactionKey,
                payment.cancellationReason,
                if (payment.status == "CANCELED") payment.amount else 0,
            )
        }
        validateSecretKey()
        try {
            val response = restClient.get().uri(PAYMENT_PATH, paymentKey).retrieve().body(TossPaymentResponse::class.java)
                ?: throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
            return PgPaymentQueryResult(response.paymentKey, response.orderId, response.totalAmount, response.status,
                convertPaymentMethod(response.method, null), response.cancels?.lastOrNull()?.transactionKey,
                response.cancels?.lastOrNull()?.cancelReason, response.cancels?.sumOf { it.cancelAmount } ?: 0)
        } catch (exception: ResourceAccessException) {
            throw CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        }
    }

    private fun validateSecretKey() {
        if (!StringUtils.hasText(secretKey)) throw CustomException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED)
    }

    private fun isLocalDemoPayment(paymentKey: String?): Boolean =
        localDemoEnabled && paymentKey?.startsWith(LOCAL_DEMO_PAYMENT_KEY_PREFIX) == true

    private fun convertPaymentMethod(method: String?, fallback: PaymentMethod?): PaymentMethod? = when (method) {
        "카드" -> PaymentMethod.CARD
        "간편결제" -> PaymentMethod.EASY_PAY
        else -> fallback
    }

    private data class TossPaymentConfirmRequest(val paymentKey: String, val orderId: String, val amount: Int)
    private data class LocalDemoPayment(
        val paymentKey: String,
        val pgOrderId: String,
        val amount: Int,
        val paymentMethod: PaymentMethod,
        val status: String,
        val cancellationTransactionKey: String?,
        val cancellationReason: String?,
    )
    private data class TossPaymentCancelRequest(val cancelReason: String)
    private data class TossPaymentResponse(val paymentKey: String, val orderId: String, val totalAmount: Int, val status: String, val method: String?, val cancels: List<TossCancelResponse>?)
    private data class TossCancelResponse(val cancelAmount: Int, val cancelReason: String?, val transactionKey: String?, val cancelStatus: String)

    companion object {
        private const val CONFIRM_PATH = "/v1/payments/confirm"
        private const val CANCEL_PATH = "/v1/payments/{paymentKey}/cancel"
        private const val PAYMENT_PATH = "/v1/payments/{paymentKey}"
        private const val LOCAL_DEMO_PAYMENT_KEY_PREFIX = "local-demo-"
    }
}
