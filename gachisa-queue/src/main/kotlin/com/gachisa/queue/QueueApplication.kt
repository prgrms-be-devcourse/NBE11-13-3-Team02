package com.gachisa.queue

import com.gachisa.queue.core.QueueProperties
import com.gachisa.queue.core.QueueService
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(QueueProperties::class)
class QueueApplication {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}

fun main(args: Array<String>) {
    runApplication<QueueApplication>(*args)
}

/**
 * 만료된 입장을 되돌리고 다음 사람을 입장시킨다.
 *
 * `@Scheduled`는 suspend 함수를 부를 수 없어 코루틴 스코프에서 띄운다. 스케줄러 스레드를
 * 붙잡지 않으므로 큐가 많아져도 스케줄러 풀이 막히지 않는다.
 */
@Component
class QueueAdmissionScheduler(private val queueService: QueueService) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Scheduled(fixedDelayString = "\${queue.scheduler-interval:PT10S}")
    fun sweep() {
        scope.launch {
            runCatching { queueService.processAllQueues() }
                .onFailure { log.error("대기열 정리 실패", it) }
        }
    }
}
