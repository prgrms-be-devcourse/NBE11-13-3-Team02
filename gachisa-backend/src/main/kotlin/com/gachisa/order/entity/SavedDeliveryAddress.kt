package com.gachisa.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "saved_delivery_address")
class SavedDeliveryAddress(
    @Column(nullable = false)
    val buyerId: Long,

    @Column(nullable = false, length = 30)
    var addressName: String,

    @Column(nullable = false, length = 30)
    var recipientName: String,

    @Column(nullable = false, length = 20)
    var recipientPhone: String,

    @Column(nullable = false, length = 10)
    var zipCode: String,

    @Column(nullable = false, length = 200)
    var address: String,

    @Column(nullable = false, length = 200)
    var addressDetail: String,

    @Column(length = 200)
    var deliveryRequest: String?,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(nullable = false)
    var updatedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun update(
        addressName: String,
        recipientName: String,
        recipientPhone: String,
        zipCode: String,
        address: String,
        addressDetail: String,
        deliveryRequest: String?,
        updatedAt: LocalDateTime,
    ) {
        this.addressName = addressName
        this.recipientName = recipientName
        this.recipientPhone = recipientPhone
        this.zipCode = zipCode
        this.address = address
        this.addressDetail = addressDetail
        this.deliveryRequest = deliveryRequest
        this.updatedAt = updatedAt
    }
}
