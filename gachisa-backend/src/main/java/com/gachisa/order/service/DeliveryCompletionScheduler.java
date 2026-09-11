package com.gachisa.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryCompletionScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${order.delivery-completion-delay-ms:60000}")
    public void completeDeliveries() {
        orderService.completeDeliveriesDue();
    }
}
