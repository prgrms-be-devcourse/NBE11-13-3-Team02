package com.gachisa.payment.entity;

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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "toss_webhook_event")
public class TossWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transmissionId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String paymentKey;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Builder
    private TossWebhookEvent(String transmissionId, String eventType, String paymentKey,
                             LocalDateTime receivedAt) {
        this.transmissionId = transmissionId;
        this.eventType = eventType;
        this.paymentKey = paymentKey;
        this.receivedAt = receivedAt;
    }
}
