package com.gachisa.payment.repository

import com.gachisa.payment.entity.PaymentAttempt
import com.gachisa.payment.entity.PaymentAttemptStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface PaymentAttemptRepository : JpaRepository<PaymentAttempt, Long> {
    fun findByClientRequestId(clientRequestId: String): Optional<PaymentAttempt>
    fun findByPgOrderId(pgOrderId: String): Optional<PaymentAttempt>

    @Query("select pa.paymentId from PaymentAttempt pa where pa.id = :attemptId")
    fun findPaymentIdByAttemptId(@Param("attemptId") attemptId: Long): Optional<Long>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pa from PaymentAttempt pa where pa.id = :attemptId")
    fun findByIdForUpdate(@Param("attemptId") attemptId: Long): Optional<PaymentAttempt>

    fun findFirstByPaymentIdOrderByCreatedAtDesc(paymentId: Long): Optional<PaymentAttempt>

    fun findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
        paymentId: Long,
        status: PaymentAttemptStatus,
    ): Optional<PaymentAttempt>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
        paymentId: Long,
        statuses: Collection<PaymentAttemptStatus>,
    ): Optional<PaymentAttempt>

    fun findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
        status: PaymentAttemptStatus,
        nextRetryAt: LocalDateTime,
    ): List<PaymentAttempt>
}
