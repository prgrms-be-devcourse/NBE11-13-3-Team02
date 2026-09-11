package com.gachisa.order.dto;

import com.gachisa.order.entity.DeliveryStatus;
import com.gachisa.order.entity.Order;
import java.time.LocalDateTime;

public record DeliveryResponse(
        Long orderId,
        String orderNumber,
        Long productId,
        String productName,
        String productImageUrl,
        int quantity,
        int amount,
        DeliveryStatus deliveryStatus,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address,
        String addressDetail,
        String deliveryRequest,
        String carrier,
        String trackingNumber,
        LocalDateTime preparationStartedAt,
        LocalDateTime shippingStartedAt,
        LocalDateTime expectedDeliveryAt,
        LocalDateTime deliveredAt
) {

    private static final String SELF_DELIVERY = "자체배송";

    public static DeliveryResponse from(Order order) {
        LocalDateTime expectedDeliveryAt = order.getShippingStartedAt() != null
                ? order.getShippingStartedAt().plusDays(2)
                : order.getPreparationStartedAt() == null
                        ? null
                        : order.getPreparationStartedAt().plusDays(3);

        return new DeliveryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getProductId(),
                order.getProductName(),
                order.getProductImageUrl(),
                order.getQuantity(),
                order.getAmount(),
                order.getDeliveryStatus(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getZipCode(),
                order.getAddress(),
                order.getAddressDetail(),
                order.getDeliveryRequest(),
                SELF_DELIVERY,
                null,
                order.getPreparationStartedAt(),
                order.getShippingStartedAt(),
                expectedDeliveryAt,
                order.getDeliveredAt()
        );
    }
}
