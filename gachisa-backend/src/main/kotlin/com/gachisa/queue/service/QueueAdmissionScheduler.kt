package com.gachisa.queue.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class QueueAdmissionScheduler(private val queueService: QueueService) {
    private val log = LoggerFactory.getLogger(QueueAdmissionScheduler::class.java)

    @Scheduled(fixedDelayString = "\${queue.admission-scan-delay-ms:1000}")
    fun processQueues() {
        try {
            queueService.processAllQueues()
        } catch (exception: RuntimeException) {
            log.warn("대기열 처리 중 오류가 발생했습니다.", exception)
        }
    }
}
