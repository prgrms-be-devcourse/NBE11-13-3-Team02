package com.gachisa.payment.dto

data class PaymentCancellationResponse(
    val result: String,
    val refund: RefundResponse?,
) {
    companion object {
        fun cancelled(): PaymentCancellationResponse =
            PaymentCancellationResponse("PARTICIPATION_CANCELLED", null)

        fun refundRequested(refund: RefundResponse): PaymentCancellationResponse =
            PaymentCancellationResponse("REFUND_REQUESTED", refund)
    }
}
