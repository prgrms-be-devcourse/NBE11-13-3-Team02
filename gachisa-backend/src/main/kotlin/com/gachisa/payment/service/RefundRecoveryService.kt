package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.client.PgClient
import com.gachisa.payment.client.dto.PgCancellationResult
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.service.dto.RefundRecoveryTarget
import org.springframework.stereotype.Service

@Service
class RefundRecoveryService(
    private val refundStateService: RefundStateService,
    private val refundCompletionService: RefundCompletionService,
    private val refundService: RefundService,
    private val pgClient: PgClient,
) {
    fun recover(refundId: Long): RefundResponse {
        val target = refundStateService.getRecoveryTarget(refundId)
        val payment = try {
            pgClient.getPayment(target.paymentKey)
        } catch (exception: CustomException) {
            refundStateService.keepPending(refundId, exception.getErrorCode())
            throw exception
        }

        if (payment.status == "CANCELED") {
            validateCancellation(target, payment)
            return refundCompletionService.complete(
                refundId,
                PgCancellationResult(
                    paymentKey = payment.paymentKey,
                    pgOrderId = payment.pgOrderId,
                    cancellationTransactionKey = payment.cancellationTransactionKey,
                    cancelledAmount = payment.cancelledAmount,
                ),
            )
        }
        if (payment.status == "DONE") {
            refundStateService.retryPending(refundId)
            return refundService.processPending(refundId)
        }

        refundStateService.retryPending(refundId)
        return refundStateService.getRefund(refundId)
    }

    private fun validateCancellation(target: RefundRecoveryTarget, payment: PgPaymentQueryResult) {
        if (target.paymentKey != payment.paymentKey ||
            target.pgOrderId != payment.pgOrderId ||
            target.amount != payment.cancelledAmount ||
            payment.cancellationTransactionKey == null
        ) {
            throw CustomException(ErrorCode.PAYMENT_GATEWAY_INVALID_RESPONSE)
        }
    }
}
