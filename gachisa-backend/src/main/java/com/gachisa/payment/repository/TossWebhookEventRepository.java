package com.gachisa.payment.repository;

import com.gachisa.payment.entity.TossWebhookEvent;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TossWebhookEventRepository extends JpaRepository<TossWebhookEvent, Long> {

    @Modifying
    @Query(value = """
            insert ignore into toss_webhook_event
                (transmission_id, event_type, payment_key, received_at)
            values
                (:transmissionId, :eventType, :paymentKey, :receivedAt)
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("transmissionId") String transmissionId,
            @Param("eventType") String eventType,
            @Param("paymentKey") String paymentKey,
            @Param("receivedAt") LocalDateTime receivedAt
    );
}
