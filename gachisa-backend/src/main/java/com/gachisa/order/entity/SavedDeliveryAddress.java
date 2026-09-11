package com.gachisa.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "saved_delivery_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedDeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false, length = 30)
    private String addressName;

    @Column(nullable = false, length = 30)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 200)
    private String addressDetail;

    @Column(length = 200)
    private String deliveryRequest;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private SavedDeliveryAddress(Long buyerId, String addressName, String recipientName,
                                 String recipientPhone, String zipCode, String address,
                                 String addressDetail, String deliveryRequest,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.buyerId = buyerId;
        update(addressName, recipientName, recipientPhone, zipCode, address,
                addressDetail, deliveryRequest, updatedAt);
        this.createdAt = createdAt;
    }

    public void update(String addressName, String recipientName, String recipientPhone,
                       String zipCode, String address, String addressDetail,
                       String deliveryRequest, LocalDateTime updatedAt) {
        this.addressName = addressName;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.deliveryRequest = deliveryRequest;
        this.updatedAt = updatedAt;
    }
}
