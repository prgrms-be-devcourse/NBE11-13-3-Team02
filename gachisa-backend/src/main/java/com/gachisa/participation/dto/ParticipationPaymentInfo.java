package com.gachisa.participation.dto;

public record ParticipationPaymentInfo(
        Long participationId,
        Long userId,
        Long groupBuyId,
        int quantity,
        boolean payable
) {
}
