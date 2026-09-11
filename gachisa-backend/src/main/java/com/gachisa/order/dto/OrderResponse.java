package com.gachisa.order.dto;

import com.gachisa.order.entity.DeliveryStatus;
import com.gachisa.order.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String orderNumber,
        Long participationId,
        Long paymentId,
        Long groupBuyId,
        Long productId,
        String productName,
        String productImageUrl,
        int quantity,
        int basePrice,
        BigDecimal discountRate,
        int discountAmount,
        int amount,
        boolean deliveryAddressRegistered,
        DeliveryStatus deliveryStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getParticipationId(),
                order.getPaymentId(),
                order.getGroupBuyId(),
                order.getProductId(),
                order.getProductName(),
                order.getProductImageUrl(),
                order.getQuantity(),
                order.getBasePrice(),
                order.getDiscountRate(),
                order.getDiscountAmount(),
                order.getAmount(),
                order.getAddress() != null,
                order.getDeliveryStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
