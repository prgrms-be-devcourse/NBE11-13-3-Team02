package com.gachisa.order.dto;

import com.gachisa.order.entity.SavedDeliveryAddress;

public record SavedDeliveryAddressResponse(
        Long id,
        String addressName,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address,
        String addressDetail,
        String deliveryRequest
) {
    public static SavedDeliveryAddressResponse from(SavedDeliveryAddress saved) {
        return new SavedDeliveryAddressResponse(saved.getId(), saved.getAddressName(),
                saved.getRecipientName(), saved.getRecipientPhone(), saved.getZipCode(),
                saved.getAddress(), saved.getAddressDetail(), saved.getDeliveryRequest());
    }
}
