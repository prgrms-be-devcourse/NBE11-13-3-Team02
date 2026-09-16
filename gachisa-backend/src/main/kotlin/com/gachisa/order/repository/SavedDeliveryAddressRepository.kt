package com.gachisa.order.repository

import com.gachisa.order.entity.SavedDeliveryAddress
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SavedDeliveryAddressRepository : JpaRepository<SavedDeliveryAddress, Long> {
    fun findAllByBuyerIdOrderByUpdatedAtDesc(buyerId: Long): List<SavedDeliveryAddress>
    fun findByIdAndBuyerId(id: Long, buyerId: Long): Optional<SavedDeliveryAddress>
}
