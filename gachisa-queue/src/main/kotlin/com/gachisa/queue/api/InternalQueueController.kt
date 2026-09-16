package com.gachisa.queue.api

import com.gachisa.queue.core.QueueService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class BindPaymentAttemptRequest(val paymentAttemptId: Long)

/**
 * core(gachisa-backend)만 호출하는 서비스 간 API.
 *
 * 사용자 토큰이 아니라 공유 시크릿으로 인증한다([com.gachisa.queue.security.InternalTokenFilter]).
 * 브라우저에 노출되면 남의 대기열 상태를 조작할 수 있으므로 경로를 internal 로 분리했다.
 */
@RestController
@RequestMapping("/internal/queues/{groupBuyId}/users/{userId}")
class InternalQueueController(private val queueService: QueueService) {

    @PostMapping("/require-admission")
    suspend fun requireAdmission(
        @PathVariable groupBuyId: Long,
        @PathVariable userId: Long,
        @RequestParam queueToken: String,
    ) = queueService.requireAdmission(groupBuyId, userId, queueToken)

    @PostMapping("/payment-attempt")
    suspend fun bindPaymentAttempt(
        @PathVariable groupBuyId: Long,
        @PathVariable userId: Long,
        @RequestBody request: BindPaymentAttemptRequest,
    ) = queueService.bindPaymentAttempt(groupBuyId, userId, request.paymentAttemptId)

    @PostMapping("/start-confirmation")
    suspend fun startConfirmation(
        @PathVariable groupBuyId: Long,
        @PathVariable userId: Long,
    ) = queueService.startConfirmation(groupBuyId, userId)

    @PostMapping("/confirmation-failed")
    suspend fun confirmationFailed(
        @PathVariable groupBuyId: Long,
        @PathVariable userId: Long,
    ) = queueService.confirmationFailed(groupBuyId, userId)

    @PostMapping("/complete")
    suspend fun completeAdmission(
        @PathVariable groupBuyId: Long,
        @PathVariable userId: Long,
    ) = queueService.completeAdmission(groupBuyId, userId)
}
