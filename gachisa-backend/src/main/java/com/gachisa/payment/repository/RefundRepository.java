package com.gachisa.payment.repository;

import com.gachisa.payment.entity.Refund;
import com.gachisa.payment.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentId(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.paymentId = :paymentId")
    Optional<Refund> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :refundId")
    Optional<Refund> findByIdForUpdate(@Param("refundId") Long refundId);

    List<Refund> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<RefundStatus> statuses,
            LocalDateTime nextRetryAt
    );
}
