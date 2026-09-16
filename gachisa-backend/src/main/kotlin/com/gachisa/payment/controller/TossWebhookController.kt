package com.gachisa.payment.controller

import com.gachisa.payment.dto.TossPaymentWebhookRequest
import com.gachisa.payment.dto.TossWebhookResponse
import com.gachisa.payment.service.TossWebhookService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/webhooks/toss")
class TossWebhookController(private val tossWebhookService: TossWebhookService) {
    @PostMapping("/payments")
    fun paymentStatusChanged(
        @RequestHeader(value = "tosspayments-webhook-transmission-id", required = false)
        transmissionId: String?,
        @Valid @RequestBody request: TossPaymentWebhookRequest,
    ): TossWebhookResponse = tossWebhookService.process(transmissionId, request)
}
