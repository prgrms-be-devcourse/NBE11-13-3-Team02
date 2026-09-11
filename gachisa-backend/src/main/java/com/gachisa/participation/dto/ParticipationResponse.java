package com.gachisa.participation.dto;

import com.gachisa.participation.entity.Participation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ParticipationResponse {

    private final Long participationId;
    private final Long groupBuyId;
    private final String productName;
    private final Integer quantity;
    private final String status;
    private final LocalDateTime participatedAt;

    private ParticipationResponse(Participation p) {
        this.participationId = p.getId();
        this.groupBuyId = p.getGroupBuy().getId();
        this.productName = p.getGroupBuy().getProduct().getName();
        this.quantity = p.getQuantity();
        this.status = p.getStatus().getLabel();
        this.participatedAt = p.getParticipatedAt();
    }

    public static ParticipationResponse from(Participation participation) {
        return new ParticipationResponse(participation);
    }
}
