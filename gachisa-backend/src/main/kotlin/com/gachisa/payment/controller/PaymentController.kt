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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Payment", description = "결제 생성/승인/조회, 참여 취소·환불. 모두 로그인 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
class PaymentController(
    private val paymentService: PaymentService,
    private val paymentCancellationService: PaymentCancellationService,
) {
    @Operation(summary = "참여 취소(결제 후)", description = "결제 완료 이후의 참여를 취소하고 환불을 요청합니다.")
    @PostMapping("/participations/{participationId}/cancel")
    fun cancelParticipation(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): PaymentCancellationResponse = paymentCancellationService.cancel(participationId, requireUserId(userId))

    @Operation(summary = "환불 상태 조회")
    @GetMapping("/participations/{participationId}/refund")
    fun getRefundStatus(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): RefundResponse = paymentCancellationService.getRefundStatus(participationId, requireUserId(userId))

    @Operation(summary = "결제 생성", description = "PG 결제창을 띄우기 전 결제 시도를 등록합니다. Idempotency-Key와 Queue-Token 헤더가 필요합니다.")
    @PostMapping("/participations/{participationId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @PathVariable participationId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Parameter(description = "요청 멱등키 (UUID v4)", required = true) @RequestHeader("Idempotency-Key") clientRequestId: String,
        @Parameter(description = "결제 대기열 토큰", required = true) @RequestHeader("Queue-Token") queueToken: String,
        @Valid @RequestBody request: PaymentRequest,
    ): PaymentResponse = paymentService.createPayment(
        participationId,
        requireUserId(userId),
        clientRequestId,
        queueToken,
        request,
    )

    @Operation(summary = "결제 승인 (attemptId 기준)")
    @PostMapping("/payment-attempts/{paymentAttemptId}/confirm")
    fun confirmPayment(
        @PathVariable paymentAttemptId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: PaymentConfirmRequest,
    ): PaymentResponse = paymentService.confirmPayment(paymentAttemptId, requireUserId(userId), request)

    @Operation(summary = "결제 승인 (PG 주문번호 기준)")
    @PostMapping("/payments/confirm")
    fun confirmPaymentByPgOrderId(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: PaymentConfirmRequest,
    ): PaymentResponse = paymentService.confirmPaymentByPgOrderId(requireUserId(userId), request)

    @Operation(summary = "PG 주문번호로 결제 조회")
    @GetMapping("/payments/pg-orders/{pgOrderId}")
    fun getPaymentByPgOrderId(
        @PathVariable pgOrderId: String,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): PaymentResponse = paymentService.getPaymentByPgOrderId(pgOrderId, requireUserId(userId))

    @Operation(summary = "결제 단건 조회")
    @GetMapping("/payments/{paymentId}")
    fun getPayment(
        @PathVariable paymentId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): PaymentResponse = paymentService.getPayment(paymentId, requireUserId(userId))

    private fun requireUserId(userId: Long?): Long =
        userId ?: throw CustomException(ErrorCode.FORBIDDEN)
}
