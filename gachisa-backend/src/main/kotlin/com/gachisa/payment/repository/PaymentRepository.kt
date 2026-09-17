package com.gachisa.payment.repository

import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByParticipationId(participationId: Long): Optional<Payment>

    @Modifying
    @Query(
        value = """
            insert into payment (participation_id, amount, status, created_at, updated_at)
            values (:participationId, :amount, 'READY', :createdAt, :createdAt)
            on duplicate key update id = last_insert_id(id)
        """,
        nativeQuery = true,
    )
    fun insertReadyIfAbsent(
        @Param("participationId") participationId: Long,
        @Param("amount") amount: Int,
        @Param("createdAt") createdAt: LocalDateTime,
    )

    fun findAllByParticipationIdIn(participationIds: Collection<Long>): List<Payment>

    fun findAllByParticipationIdInAndStatus(
        participationIds: Collection<Long>,
        status: PaymentStatus,
    ): List<Payment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    fun findByIdForUpdate(@Param("paymentId") paymentId: Long): Optional<Payment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.participationId = :participationId")
    fun findByParticipationIdForUpdate(@Param("participationId") participationId: Long): Optional<Payment>
}
