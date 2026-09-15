package com.gachisa.queue.api

import com.gachisa.queue.core.QueueService
import com.gachisa.queue.core.QueueStatusResponse
import com.gachisa.queue.core.QueueTokenResponse
import com.gachisa.queue.security.AccessTokenVerifier
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 브라우저가 직접 호출하는 대기열 API. 경로는 분리 전 core와 동일하게 유지한다.
 * 프론트엔드는 게이트웨이/프록시 설정만 바뀌고 코드는 그대로다.
 */
@RestController
@RequestMapping("/api/group-buys/{groupBuyId}/queue-token")
class QueueController(
    private val queueService: QueueService,
    private val tokenVerifier: AccessTokenVerifier,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun issueToken(
        @PathVariable groupBuyId: Long,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
    ): QueueTokenResponse =
        queueService.issueToken(groupBuyId, tokenVerifier.userIdOf(authorization))

    @GetMapping("/{queueToken}/status")
    suspend fun getStatus(
        @PathVariable groupBuyId: Long,
        @PathVariable queueToken: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
    ): QueueStatusResponse =
        queueService.getStatus(groupBuyId, tokenVerifier.userIdOf(authorization), queueToken)
}
