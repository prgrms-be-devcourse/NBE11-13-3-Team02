package com.gachisa.order.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DeliveryCompletionScheduler(private val orderService: OrderService) {
    @Scheduled(fixedDelayString = "\${order.delivery-completion-delay-ms:60000}")
    fun completeDeliveries() {
        orderService.completeDeliveriesDue()
    }
}
