package com.gachisa.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private final QueueService queueService;

    @Scheduled(fixedDelayString = "${queue.admission-scan-delay-ms:1000}")
    public void processQueues() {
        try {
            queueService.processAllQueues();
        } catch (RuntimeException exception) {
            log.warn("대기열 처리 중 오류가 발생했습니다.", exception);
        }
    }
}
