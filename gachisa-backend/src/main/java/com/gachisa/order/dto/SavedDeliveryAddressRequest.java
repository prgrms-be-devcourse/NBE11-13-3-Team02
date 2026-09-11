package com.gachisa.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "저장 배송지 등록/수정 요청")
public record SavedDeliveryAddressRequest(
        @Schema(description = "배송지 별칭 (예: 집, 회사)", example = "집")
        @NotBlank @Size(max = 30) String addressName,
        @Schema(description = "수령인 이름", example = "홍길동")
        @NotBlank @Size(max = 30) String recipientName,
        @Schema(description = "수령인 연락처 (숫자와 '-'만 허용)", example = "010-1234-5678")
        @NotBlank @Pattern(regexp = "^[0-9-]{9,20}$") String recipientPhone,
        @Schema(description = "우편번호 (5자리 숫자)", example = "06236")
        @NotBlank @Pattern(regexp = "^[0-9]{5}$") String zipCode,
        @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
        @NotBlank @Size(max = 200) String address,
        @Schema(description = "상세 주소", example = "101동 1001호")
        @NotBlank @Size(max = 200) String addressDetail,
        @Schema(description = "배송 요청사항 (선택)", example = "부재 시 경비실에 맡겨주세요")
        @Size(max = 200) String deliveryRequest
) {
}
