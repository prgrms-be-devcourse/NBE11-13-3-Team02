package com.gachisa.order.dto;

public record OrderCreateCommand(
        Long participationId,
        Long paymentId,
        Long buyerId,
        Long groupBuyId,
        int quantity,
        int amount
) {
}
