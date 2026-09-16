package com.gachisa.order.controller

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.order.dto.SavedDeliveryAddressRequest
import com.gachisa.order.dto.SavedDeliveryAddressResponse
import com.gachisa.order.service.SavedDeliveryAddressService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/delivery-addresses")
class SavedDeliveryAddressController(private val service: SavedDeliveryAddressService) {
    @GetMapping
    fun getMyAddresses(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): List<SavedDeliveryAddressResponse> = service.getMyAddresses(requireUserId(userId))

    @PostMapping
    fun create(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: SavedDeliveryAddressRequest,
    ): SavedDeliveryAddressResponse = service.create(requireUserId(userId), request)

    @PatchMapping("/{addressId}")
    fun update(
        @PathVariable addressId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: SavedDeliveryAddressRequest,
    ): SavedDeliveryAddressResponse = service.update(addressId, requireUserId(userId), request)

    @DeleteMapping("/{addressId}")
    fun delete(
        @PathVariable addressId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ) {
        service.delete(addressId, requireUserId(userId))
    }

    private fun requireUserId(userId: Long?): Long =
        userId ?: throw CustomException(ErrorCode.FORBIDDEN)
}
