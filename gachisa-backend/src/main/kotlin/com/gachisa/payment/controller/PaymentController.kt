package com.gachisa.payment.controller

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.payment.dto.PaymentCancellationResponse
import com.gachisa.payment.dto.PaymentConfirmRequest
import com.gachisa.payment.dto.PaymentRequest
import com.gachisa.payment.dto.PaymentResponse
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.service.PaymentCancellationService
import com.gachisa.payment.service.PaymentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class PaymentController(
    private val paymentService: PaymentService,
    private val paymentCancellationService: PaymentCancellationService,
) {
    @PostMapping("/participations/{participationId}/cancel")
    fun cancelParticipation(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): PaymentCancellationResponse = paymentCancellationService.cancel(participationId, requireUserId(userId))

    @GetMapping("/participations/{participationId}/refund")
    fun getRefundStatus(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): RefundResponse = paymentCancellationService.getRefundStatus(participationId, requireUserId(userId))

    @PostMapping("/participations/{participationId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @RequestHeader("Idempotency-Key") clientRequestId: String,
        @RequestHeader("Queue-Token") queueToken: String,
        @Valid @RequestBody request: PaymentRequest,
    ): PaymentResponse = paymentService.createPayment(
        participationId,
        requireUserId(userId),
        clientRequestId,
        queueToken,
        request,
    )

    @PostMapping("/payment-attempts/{paymentAttemptId}/confirm")
    fun confirmPayment(
        @PathVariable paymentAttemptId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: PaymentConfirmRequest,
    ): PaymentResponse = paymentService.confirmPayment(paymentAttemptId, requireUserId(userId), request)

    @PostMapping("/payments/confirm")
    fun confirmPaymentByPgOrderId(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: PaymentConfirmRequest,
    ): PaymentResponse = paymentService.confirmPaymentByPgOrderId(requireUserId(userId), request)

    @GetMapping("/payments/pg-orders/{pgOrderId}")
    fun getPaymentByPgOrderId(
        @PathVariable pgOrderId: String,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): PaymentResponse = paymentService.getPaymentByPgOrderId(pgOrderId, requireUserId(userId))

    @GetMapping("/payments/{paymentId}")
    fun getPayment(
        @PathVariable paymentId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): PaymentResponse = paymentService.getPayment(paymentId, requireUserId(userId))

    private fun requireUserId(userId: Long?): Long =
        userId ?: throw CustomException(ErrorCode.FORBIDDEN)
}
