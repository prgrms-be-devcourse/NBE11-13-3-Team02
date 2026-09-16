package com.gachisa.order.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SavedDeliveryAddressRequest(
    @field:NotBlank @field:Size(max = 30) val addressName: String,
    @field:NotBlank @field:Size(max = 30) val recipientName: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9-]{9,20}$") val recipientPhone: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{5}$") val zipCode: String,
    @field:NotBlank @field:Size(max = 200) val address: String,
    @field:NotBlank @field:Size(max = 200) val addressDetail: String,
    @field:Size(max = 200) val deliveryRequest: String?,
)
