package com.gachisa.payment.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    public void recordPaymentConfirmation(String result) {
        increment("gachisa.payment.confirmation", result);
    }

    public void recordPaymentWebhook(String result) {
        increment("gachisa.payment.webhook", result);
    }

    public void recordPaymentRecovery(String result) {
        increment("gachisa.payment.recovery", result);
    }

    public void recordRefund(String result) {
        increment("gachisa.refund", result);
    }

    public <T> T recordPaymentConfirmationTime(Supplier<T> action) {
        return Timer.builder("gachisa.payment.confirmation")
                .tag("operation", "pg_confirm")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(action);
    }

    public <T> T recordRefundTime(Supplier<T> action) {
        return Timer.builder("gachisa.refund")
                .tag("operation", "pg_cancel")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(action);
    }

    private void increment(String name, String result) {
        meterRegistry.counter(name + ".total", "result", result).increment();
    }
}
