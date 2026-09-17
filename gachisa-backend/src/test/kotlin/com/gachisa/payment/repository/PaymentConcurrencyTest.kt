package com.gachisa.payment.repository

import com.gachisa.payment.entity.Payment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class PaymentConcurrencyTest {
    @Autowired private lateinit var paymentRepository: PaymentRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Test fun concurrentPaymentCreationCreatesOnlyOnePayment() {
        val participationId = 9_000_000_001L; val ready = CountDownLatch(2); val start = CountDownLatch(1); val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { insertPayment(participationId, ready, start) }; val second = executor.submit { insertPayment(participationId, ready, start) }
            ready.await(); start.countDown(); first.get(); second.get()
            assertThat(paymentRepository.findByParticipationId(participationId)).isPresent.map(Payment::amount).hasValue(10_000)
            assertThat(paymentRepository.count()).isEqualTo(1L)
        } finally { executor.shutdownNow() }
    }
    private fun insertPayment(participationId: Long, ready: CountDownLatch, start: CountDownLatch) {
        try { ready.countDown(); start.await(); TransactionTemplate(transactionManager).executeWithoutResult { paymentRepository.insertReadyIfAbsent(participationId, 10_000, LocalDateTime.now()) } }
        catch (exception: InterruptedException) { Thread.currentThread().interrupt(); throw IllegalStateException(exception) }
    }
}
