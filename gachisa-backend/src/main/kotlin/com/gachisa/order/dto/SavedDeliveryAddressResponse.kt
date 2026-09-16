package com.gachisa.order.dto

import com.gachisa.order.entity.SavedDeliveryAddress

data class SavedDeliveryAddressResponse(
    val id: Long,
    val addressName: String,
    val recipientName: String,
    val recipientPhone: String,
    val zipCode: String,
    val address: String,
    val addressDetail: String,
    val deliveryRequest: String?,
) {
    companion object {
        fun from(saved: SavedDeliveryAddress): SavedDeliveryAddressResponse = SavedDeliveryAddressResponse(
            id = saved.id!!,
            addressName = saved.addressName,
            recipientName = saved.recipientName,
            recipientPhone = saved.recipientPhone,
            zipCode = saved.zipCode,
            address = saved.address,
            addressDetail = saved.addressDetail,
            deliveryRequest = saved.deliveryRequest,
        )
    }
}
