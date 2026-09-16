package com.gachisa.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class Payment(
    @Column(nullable = false, unique = true)
    val participationId: Long,

    @Column(nullable = false)
    val amount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(nullable = false)
    var updatedAt: LocalDateTime,

    var paidAt: LocalDateTime? = null,
    var refundedAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun complete(paidAt: LocalDateTime) {
        status = PaymentStatus.PAID
        this.paidAt = paidAt
        updatedAt = paidAt
    }

    fun refund(refundedAt: LocalDateTime) {
        status = PaymentStatus.REFUNDED
        this.refundedAt = refundedAt
        updatedAt = refundedAt
    }
}
