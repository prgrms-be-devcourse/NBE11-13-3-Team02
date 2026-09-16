package com.gachisa.payment.repository

import com.gachisa.payment.entity.Refund
import com.gachisa.payment.entity.RefundStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface RefundRepository : JpaRepository<Refund, Long> {
    fun findByPaymentId(paymentId: Long): Optional<Refund>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.paymentId = :paymentId")
    fun findByPaymentIdForUpdate(@Param("paymentId") paymentId: Long): Optional<Refund>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :refundId")
    fun findByIdForUpdate(@Param("refundId") refundId: Long): Optional<Refund>

    fun findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
        statuses: Collection<RefundStatus>,
        nextRetryAt: LocalDateTime,
    ): List<Refund>
}
