package com.gachisa.queue.metric;

import com.gachisa.queue.repository.QueueRedisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentQueueMetrics {

    private final MeterRegistry meterRegistry;
    private final QueueRedisRepository queueRepository;

    public PaymentQueueMetrics(MeterRegistry meterRegistry, QueueRedisRepository queueRepository) {
        this.meterRegistry = meterRegistry;
        this.queueRepository = queueRepository;
        Gauge.builder("gachisa.payment.queue.waiting", queueRepository,
                        QueueRedisRepository::countWaiting)
                .description("현재 결제 진입 대기 중인 사용자 수")
                .register(meterRegistry);
        Gauge.builder("gachisa.payment.queue.admitted", queueRepository,
                        QueueRedisRepository::countAdmitted)
                .description("현재 결제 진입이 허용된 사용자 수")
                .register(meterRegistry);
        Gauge.builder("gachisa.payment.queue.confirming", queueRepository,
                        QueueRedisRepository::countConfirming)
                .description("현재 결제 승인을 진행 중인 사용자 수")
                .register(meterRegistry);
    }

    public void record(String event, double amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder("gachisa.payment.queue.events")
                .tag("event", event)
                .register(meterRegistry)
                .increment(amount);
    }
}
