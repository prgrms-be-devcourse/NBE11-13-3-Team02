package com.gachisa.payment.repository;

import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByParticipationId(Long participationId);

    @Modifying
    @Query(value = """
            insert into payment (participation_id, amount, status, created_at, updated_at)
            values (:participationId, :amount, 'READY', :createdAt, :createdAt)
            on duplicate key update id = last_insert_id(id)
            """, nativeQuery = true)
    void insertReadyIfAbsent(
            @Param("participationId") Long participationId,
            @Param("amount") int amount,
            @Param("createdAt") LocalDateTime createdAt
    );

    List<Payment> findAllByParticipationIdIn(Collection<Long> participationIds);

    List<Payment> findAllByParticipationIdInAndStatus(
            Collection<Long> participationIds,
            PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.participationId = :participationId")
    Optional<Payment> findByParticipationIdForUpdate(@Param("participationId") Long participationId);
}
