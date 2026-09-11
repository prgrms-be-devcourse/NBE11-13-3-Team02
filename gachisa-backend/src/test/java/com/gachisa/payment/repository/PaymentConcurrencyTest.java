package com.gachisa.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachisa.payment.entity.Payment;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class PaymentConcurrencyTest {

    @Autowired PaymentRepository paymentRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentPaymentCreationCreatesOnlyOnePayment() throws Exception {
        long participationId = 9_000_000_001L;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> insertPayment(participationId, ready, start));
            Future<?> second = executor.submit(() -> insertPayment(participationId, ready, start));

            ready.await();
            start.countDown();
            first.get();
            second.get();

            assertThat(paymentRepository.findByParticipationId(participationId))
                    .isPresent()
                    .map(Payment::getAmount)
                    .hasValue(10_000);
            assertThat(paymentRepository.count()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertPayment(long participationId, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    paymentRepository.insertReadyIfAbsent(
                            participationId, 10_000, LocalDateTime.now()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
