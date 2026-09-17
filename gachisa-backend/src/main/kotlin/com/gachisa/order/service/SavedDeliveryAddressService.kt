package com.gachisa.order.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.order.dto.SavedDeliveryAddressRequest
import com.gachisa.order.dto.SavedDeliveryAddressResponse
import com.gachisa.order.entity.SavedDeliveryAddress
import com.gachisa.order.repository.SavedDeliveryAddressRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SavedDeliveryAddressService(
    private val repository: SavedDeliveryAddressRepository,
    private val timeProvider: TimeProvider,
) {
    @Transactional(readOnly = true)
    fun getMyAddresses(buyerId: Long): List<SavedDeliveryAddressResponse> =
        repository.findAllByBuyerIdOrderByUpdatedAtDesc(buyerId).map(SavedDeliveryAddressResponse::from)

    @Transactional
    fun create(buyerId: Long, request: SavedDeliveryAddressRequest): SavedDeliveryAddressResponse {
        val now = timeProvider.now()
        val saved = SavedDeliveryAddress(
            buyerId = buyerId,
            addressName = request.addressName,
            recipientName = request.recipientName,
            recipientPhone = request.recipientPhone,
            zipCode = request.zipCode,
            address = request.address,
            addressDetail = request.addressDetail,
            deliveryRequest = request.deliveryRequest,
            createdAt = now,
            updatedAt = now,
        )
        return SavedDeliveryAddressResponse.from(repository.save(saved))
    }

    @Transactional
    fun update(id: Long, buyerId: Long, request: SavedDeliveryAddressRequest): SavedDeliveryAddressResponse {
        val saved = getOwned(id, buyerId)
        saved.update(
            request.addressName,
            request.recipientName,
            request.recipientPhone,
            request.zipCode,
            request.address,
            request.addressDetail,
            request.deliveryRequest,
            timeProvider.now(),
        )
        return SavedDeliveryAddressResponse.from(saved)
    }

    @Transactional
    fun delete(id: Long, buyerId: Long) {
        repository.delete(getOwned(id, buyerId))
    }

    private fun getOwned(id: Long, buyerId: Long): SavedDeliveryAddress =
        repository.findByIdAndBuyerId(id, buyerId)
            .orElseThrow { CustomException(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND) }
}
