package com.gachisa.order.controller

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.order.dto.SavedDeliveryAddressRequest
import com.gachisa.order.dto.SavedDeliveryAddressResponse
import com.gachisa.order.service.SavedDeliveryAddressService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "SavedDeliveryAddress", description = "자주 쓰는 배송지 저장/조회/수정/삭제. 모두 로그인 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users/me/delivery-addresses")
class SavedDeliveryAddressController(private val service: SavedDeliveryAddressService) {
    @Operation(summary = "내 저장 배송지 목록 조회")
    @GetMapping
    fun getMyAddresses(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
    ): List<SavedDeliveryAddressResponse> = service.getMyAddresses(requireUserId(userId))

    @Operation(summary = "저장 배송지 등록")
    @PostMapping
    fun create(
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: SavedDeliveryAddressRequest,
    ): SavedDeliveryAddressResponse = service.create(requireUserId(userId), request)

    @Operation(summary = "저장 배송지 수정")
    @PatchMapping("/{addressId}")
    fun update(
        @PathVariable addressId: Long,
        @AuthenticationPrincipal(expression = "userId") userId: Long?,
        @Valid @RequestBody request: SavedDeliveryAddressRequest,
    ): SavedDeliveryAddressResponse = service.update(addressId, requireUserId(userId), request)

    @Operation(summary = "저장 배송지 삭제")
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
