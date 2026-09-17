package com.gachisa.order.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "저장 배송지 등록/수정 요청")
data class SavedDeliveryAddressRequest(
    @field:Schema(description = "배송지 별칭", example = "집")
    @field:NotBlank @field:Size(max = 30) val addressName: String,
    @field:Schema(description = "수령인 이름", example = "홍길동")
    @field:NotBlank @field:Size(max = 30) val recipientName: String,
    @field:Schema(description = "수령인 연락처", example = "010-1234-5678")
    @field:NotBlank @field:Pattern(regexp = "^[0-9-]{9,20}$") val recipientPhone: String,
    @field:Schema(description = "우편번호", example = "06236")
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{5}$") val zipCode: String,
    @field:Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
    @field:NotBlank @field:Size(max = 200) val address: String,
    @field:Schema(description = "상세 주소", example = "101동 1001호")
    @field:NotBlank @field:Size(max = 200) val addressDetail: String,
    @field:Schema(description = "배송 요청사항", example = "부재 시 경비실에 맡겨주세요")
    @field:Size(max = 200) val deliveryRequest: String?,
)
