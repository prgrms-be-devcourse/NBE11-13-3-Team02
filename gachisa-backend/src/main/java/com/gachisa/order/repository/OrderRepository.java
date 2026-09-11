package com.gachisa.order.repository;

import com.gachisa.order.entity.Order;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByParticipationId(Long participationId);

    Optional<Order> findByPaymentId(Long paymentId);

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    Page<Order> findAllByBuyerId(Long buyerId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
               set o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.PREPARING,
                   o.preparationStartedAt = :startedAt,
                   o.updatedAt = :startedAt
             where o.groupBuyId = :groupBuyId
               and o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.WAITING_FOR_GROUP_BUY
            """)
    int startPreparationForGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                    @Param("startedAt") LocalDateTime startedAt);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
               set o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.SHIPPING,
                   o.shippingStartedAt = :startedAt,
                   o.updatedAt = :startedAt
             where o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.PREPARING
               and o.address is not null
               and o.preparationStartedAt <= :preparationDeadline
            """)
    int startShippingDue(@Param("preparationDeadline") LocalDateTime preparationDeadline,
                         @Param("startedAt") LocalDateTime startedAt);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
               set o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.DELIVERED,
                   o.deliveredAt = :completedAt,
                   o.updatedAt = :completedAt
             where o.deliveryStatus = com.gachisa.order.entity.DeliveryStatus.SHIPPING
               and o.shippingStartedAt <= :shippingDeadline
            """)
    int completeDeliveriesDue(@Param("shippingDeadline") LocalDateTime shippingDeadline,
                              @Param("completedAt") LocalDateTime completedAt);
}
