package com.gachisa.payment.controller;

import com.gachisa.payment.dto.TossPaymentWebhookRequest;
import com.gachisa.payment.dto.TossWebhookResponse;
import com.gachisa.payment.service.TossWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Toss-Webhook", description = "토스페이먼츠 결제 상태 변경 웹훅 수신 (PG사가 서버-투-서버로 호출, 프론트에서 직접 호출하지 않음)")
@RestController
@RequestMapping("/api/webhooks/toss")
@RequiredArgsConstructor
public class TossWebhookController {

    private final TossWebhookService tossWebhookService;

    @Operation(summary = "토스페이먼츠 결제 웹훅 수신")
    @PostMapping("/payments")
    public TossWebhookResponse paymentStatusChanged(
            @Parameter(description = "토스페이먼츠 웹훅 전송 ID (중복 수신 방지용)")
            @RequestHeader(value = "tosspayments-webhook-transmission-id", required = false) String transmissionId,
            @Valid @RequestBody TossPaymentWebhookRequest request
    ) {
        return tossWebhookService.process(transmissionId, request);
    }
}
