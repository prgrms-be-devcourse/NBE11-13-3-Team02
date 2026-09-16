package com.gachisa.queue.controller

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.queue.dto.QueueStatusResponse
import com.gachisa.queue.dto.QueueTokenResponse
import com.gachisa.queue.service.QueueService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/group-buys/{groupBuyId}/queue-token")
class QueueController(private val queueService: QueueService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issueToken(
        @PathVariable groupBuyId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): QueueTokenResponse = queueService.issueToken(groupBuyId, requireUserId(userId))

    @GetMapping("/{queueToken}/status")
    fun getStatus(
        @PathVariable groupBuyId: Long,
        @PathVariable queueToken: String,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): QueueStatusResponse = queueService.getStatus(groupBuyId, requireUserId(userId), queueToken)

    private fun requireUserId(userId: Long?): Long =
        userId ?: throw CustomException(ErrorCode.FORBIDDEN)
}
