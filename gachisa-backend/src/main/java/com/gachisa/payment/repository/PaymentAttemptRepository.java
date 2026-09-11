package com.gachisa.payment.repository;

import com.gachisa.payment.entity.PaymentAttempt;
import com.gachisa.payment.entity.PaymentAttemptStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByClientRequestId(String clientRequestId);

    Optional<PaymentAttempt> findByPgOrderId(String pgOrderId);

    @Query("select pa.paymentId from PaymentAttempt pa where pa.id = :attemptId")
    Optional<Long> findPaymentIdByAttemptId(@Param("attemptId") Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pa from PaymentAttempt pa where pa.id = :attemptId")
    Optional<PaymentAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);

    Optional<PaymentAttempt> findFirstByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    Optional<PaymentAttempt> findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
            Long paymentId, PaymentAttemptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
            Long paymentId, Collection<PaymentAttemptStatus> statuses);

    List<PaymentAttempt> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            PaymentAttemptStatus status,
            LocalDateTime nextRetryAt
    );
}
