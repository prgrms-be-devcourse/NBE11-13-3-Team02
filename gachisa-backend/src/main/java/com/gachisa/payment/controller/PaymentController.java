package com.gachisa.payment.controller;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.dto.PaymentConfirmRequest;
import com.gachisa.payment.dto.PaymentRequest;
import com.gachisa.payment.dto.PaymentResponse;
import com.gachisa.payment.dto.PaymentCancellationResponse;
import com.gachisa.payment.dto.RefundResponse;
import com.gachisa.payment.service.PaymentService;
import com.gachisa.payment.service.PaymentCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "결제 생성/승인/조회, 참여 취소·환불. 모두 로그인 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentCancellationService paymentCancellationService;

    @Operation(summary = "참여 취소(결제 후)", description = "결제 완료 이후의 참여를 취소하고 환불을 요청합니다.")
    @PostMapping("/participations/{participationId}/cancel")
    public PaymentCancellationResponse cancelParticipation(
            @PathVariable Long participationId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return paymentCancellationService.cancel(participationId, requireUserId(userId));
    }

    @Operation(summary = "환불 상태 조회")
    @GetMapping("/participations/{participationId}/refund")
    public RefundResponse getRefundStatus(
            @PathVariable Long participationId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return paymentCancellationService.getRefundStatus(participationId, requireUserId(userId));
    }

    @Operation(summary = "결제 생성", description = "PG(토스페이먼츠) 결제창을 띄우기 전, 서버에 결제 시도를 등록합니다. " +
            "Idempotency-Key(요청 UUID)와 Queue-Token(대기열 토큰) 헤더가 반드시 필요합니다.")
    @PostMapping("/participations/{participationId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @PathVariable Long participationId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Parameter(description = "요청 멱등키 (UUID v4)", required = true) @RequestHeader("Idempotency-Key") String clientRequestId,
            @Parameter(description = "결제 대기열 토큰", required = true) @RequestHeader("Queue-Token") String queueToken,
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.createPayment(
                participationId,
                requireUserId(userId),
                clientRequestId,
                queueToken,
                request
        );
    }

    @Operation(summary = "결제 승인 (attemptId 기준)", description = "PG 결제창에서 돌아온 뒤 최종 승인을 요청합니다.")
    @PostMapping("/payment-attempts/{paymentAttemptId}/confirm")
    public PaymentResponse confirmPayment(
            @PathVariable Long paymentAttemptId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return paymentService.confirmPayment(paymentAttemptId, requireUserId(userId), request);
    }

    @Operation(summary = "결제 승인 (PG 주문번호 기준)", description = "PG 결제창에서 돌아온 뒤 최종 승인을 요청합니다(대체 경로).")
    @PostMapping("/payments/confirm")
    public PaymentResponse confirmPaymentByPgOrderId(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return paymentService.confirmPaymentByPgOrderId(requireUserId(userId), request);
    }

    @Operation(summary = "PG 주문번호로 결제 조회")
    @GetMapping("/payments/pg-orders/{pgOrderId}")
    public PaymentResponse getPaymentByPgOrderId(
            @PathVariable String pgOrderId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return paymentService.getPaymentByPgOrderId(pgOrderId, requireUserId(userId));
    }

    @Operation(summary = "결제 단건 조회")
    @GetMapping("/payments/{paymentId}")
    public PaymentResponse getPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return paymentService.getPayment(paymentId, requireUserId(userId));
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return userId;
    }
}
