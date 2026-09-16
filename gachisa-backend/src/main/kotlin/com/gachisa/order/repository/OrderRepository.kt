package com.gachisa.order.repository

import com.gachisa.order.entity.Order
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByParticipationId(participationId: Long): Optional<Order>
    fun findByPaymentId(paymentId: Long): Optional<Order>
    fun findByOrderNumber(orderNumber: String): Optional<Order>
    fun existsByOrderNumber(orderNumber: String): Boolean
    fun findAllByBuyerId(buyerId: Long, pageable: Pageable): Page<Order>

    @Modifying(clearAutomatically = true)
    @Query(
        """
            update Order o
               set o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.PREPARING,
                   o.preparationStartedAt = :startedAt,
                   o.updatedAt = :startedAt
             where o.groupBuyId = :groupBuyId
               and o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.WAITING_FOR_GROUP_BUY
        """,
    )
    fun startPreparationForGroupBuy(
        @Param("groupBuyId") groupBuyId: Long,
        @Param("startedAt") startedAt: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        """
            update Order o
               set o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.SHIPPING,
                   o.shippingStartedAt = :startedAt,
                   o.updatedAt = :startedAt
             where o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.PREPARING
               and o.address is not null
               and o.preparationStartedAt <= :preparationDeadline
        """,
    )
    fun startShippingDue(
        @Param("preparationDeadline") preparationDeadline: LocalDateTime,
        @Param("startedAt") startedAt: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        """
            update Order o
               set o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.DELIVERED,
                   o.deliveredAt = :completedAt,
                   o.updatedAt = :completedAt
             where o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.SHIPPING
               and o.shippingStartedAt <= :shippingDeadline
        """,
    )
    fun completeDeliveriesDue(
        @Param("shippingDeadline") shippingDeadline: LocalDateTime,
        @Param("completedAt") completedAt: LocalDateTime,
    ): Int
}
