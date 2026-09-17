package com.gachisa.payment.repository

import com.gachisa.payment.entity.TossWebhookEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface TossWebhookEventRepository : JpaRepository<TossWebhookEvent, Long> {
    @Modifying
    @Query(
        value = """
            insert ignore into toss_webhook_event
                (transmission_id, event_type, payment_key, received_at)
            values
                (:transmissionId, :eventType, :paymentKey, :receivedAt)
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("transmissionId") transmissionId: String,
        @Param("eventType") eventType: String,
        @Param("paymentKey") paymentKey: String,
        @Param("receivedAt") receivedAt: LocalDateTime,
    ): Int
}
