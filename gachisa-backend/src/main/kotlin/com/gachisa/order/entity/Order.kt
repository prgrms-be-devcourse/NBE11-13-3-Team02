package com.gachisa.order.entity

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "order_table")
class Order(
    @Column(nullable = false, unique = true, length = 9, updatable = false)
    val orderNumber: String,

    @Column(nullable = false, unique = true)
    val participationId: Long,

    @Column(nullable = false, unique = true)
    val paymentId: Long,

    @Column(nullable = false)
    val buyerId: Long,

    @Column(nullable = false)
    val groupBuyId: Long,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    val productName: String,

    val productImageUrl: String?,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val amount: Int,

    @Column(nullable = false)
    val basePrice: Int,

    @Column(nullable = false, precision = 5, scale = 4)
    val discountRate: BigDecimal,

    @Column(nullable = false)
    val discountAmount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var deliveryStatus: DeliveryStatus,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(nullable = false)
    var updatedAt: LocalDateTime,

    @Column(length = 30)
    var recipientName: String? = null,

    @Column(length = 20)
    var recipientPhone: String? = null,

    @Column(length = 10)
    var zipCode: String? = null,

    @Column(length = 200)
    var address: String? = null,

    @Column(length = 200)
    var addressDetail: String? = null,

    @Column(length = 200)
    var deliveryRequest: String? = null,

    var shippingStartedAt: LocalDateTime? = null,
    var preparationStartedAt: LocalDateTime? = null,
    var deliveredAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun changeDeliveryStatus(newStatus: DeliveryStatus, changedAt: LocalDateTime) {
        if (deliveryStatus == newStatus) return
        if (!deliveryStatus.canChangeTo(newStatus)) {
            throw CustomException(ErrorCode.INVALID_DELIVERY_STATUS_TRANSITION)
        }
        deliveryStatus = newStatus
        updatedAt = changedAt
    }

    fun registerDeliveryAddress(
        recipientName: String,
        recipientPhone: String,
        zipCode: String,
        address: String,
        addressDetail: String,
        deliveryRequest: String?,
        registeredAt: LocalDateTime,
    ) {
        val addressCannotBeRegistered =
            (deliveryStatus != DeliveryStatus.WAITING_FOR_GROUP_BUY &&
                deliveryStatus != DeliveryStatus.PREPARING) || this.address != null
        if (addressCannotBeRegistered) {
            throw CustomException(ErrorCode.DELIVERY_ADDRESS_ALREADY_REGISTERED)
        }

        this.recipientName = recipientName
        this.recipientPhone = recipientPhone
        this.zipCode = zipCode
        this.address = address
        this.addressDetail = addressDetail
        this.deliveryRequest = deliveryRequest
        updatedAt = registeredAt
    }

    fun changeDeliveryStatusByAdmin(newStatus: DeliveryStatus, changedAt: LocalDateTime) {
        if (deliveryStatus == DeliveryStatus.WAITING_FOR_GROUP_BUY && newStatus != DeliveryStatus.CANCELLED) {
            throw CustomException(ErrorCode.INVALID_DELIVERY_STATUS_TRANSITION)
        }

        val deliveryAddressRequired =
            (newStatus == DeliveryStatus.SHIPPING ||
                newStatus == DeliveryStatus.DELIVERED ||
                newStatus == DeliveryStatus.RETURNING ||
                newStatus == DeliveryStatus.RETURNED) && address == null
        if (deliveryAddressRequired) {
            throw CustomException(ErrorCode.DELIVERY_ADDRESS_REQUIRED)
        }

        deliveryStatus = newStatus
        updatedAt = changedAt

        if (newStatus == DeliveryStatus.PREPARING) {
            shippingStartedAt = null
            deliveredAt = null
            return
        }
        if (shippingStartedAt == null) shippingStartedAt = changedAt
        deliveredAt = if (newStatus == DeliveryStatus.DELIVERED) changedAt else null
    }

    fun reflectRefund(refundedAt: LocalDateTime) {
        if (deliveryStatus == DeliveryStatus.CANCELLED ||
            deliveryStatus == DeliveryStatus.RETURNING ||
            deliveryStatus == DeliveryStatus.RETURNED
        ) {
            return
        }

        deliveryStatus = if (deliveryStatus == DeliveryStatus.WAITING_FOR_GROUP_BUY ||
            deliveryStatus == DeliveryStatus.PREPARING
        ) {
            DeliveryStatus.CANCELLED
        } else {
            DeliveryStatus.RETURNING
        }
        updatedAt = refundedAt
    }
}
