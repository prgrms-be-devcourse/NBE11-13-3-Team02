package com.gachisa.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "toss_webhook_event")
class TossWebhookEvent(
    @Column(nullable = false, unique = true)
    val transmissionId: String,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val paymentKey: String,

    @Column(nullable = false)
    val receivedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
