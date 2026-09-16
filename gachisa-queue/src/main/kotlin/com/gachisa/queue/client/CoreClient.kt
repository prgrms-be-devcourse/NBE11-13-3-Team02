package com.gachisa.queue.client

import com.gachisa.queue.core.GroupBuyQueueInfo
import com.gachisa.queue.core.QueueError
import com.gachisa.queue.core.QueueProperties
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * core(gachisa-backend) 호출 클라이언트.
 *
 * WebClient + 코루틴이라 응답을 기다리는 동안 스레드를 반납한다. 대기열 조회가 몰릴 때
 * core 응답이 느려져도 이 서비스의 스레드가 그만큼 묶이지 않는다.
 */
@Component
class CoreClient(properties: QueueProperties) {

    private val client = WebClient.builder()
        .baseUrl(properties.coreBaseUrl)
        .defaultHeader(INTERNAL_TOKEN_HEADER, properties.internalToken)
        .build()

    suspend fun getQueueInfo(groupBuyId: Long): GroupBuyQueueInfo =
        try {
            client.get()
                .uri("/internal/group-buys/{id}/queue-info", groupBuyId)
                .retrieve()
                .bodyToMono(GroupBuyQueueInfo::class.java)
                .awaitSingle()
        } catch (e: WebClientResponseException.NotFound) {
            throw QueueError.QUEUE_NOT_OPEN.toException()
        }

    /**
     * 입장이 만료돼 결제 시도를 되돌려야 할 때 core에 알린다.
     *
     * 실패해도 대기열 진행을 막지 않는다. core에는 별도 복구 스케줄러가 있고,
     * 여기서 예외를 올리면 다른 사용자의 입장 처리까지 멈춘다.
     */
    suspend fun expirePaymentAttempt(paymentAttemptId: Long): Boolean =
        runCatching {
            client.post()
                .uri("/internal/payment-attempts/{id}/expire", paymentAttemptId)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { it.createException() }
                .bodyToMono(Void::class.java)
                .awaitSingleOrNull()
        }.isSuccess

    private companion object {
        const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
    }
}
