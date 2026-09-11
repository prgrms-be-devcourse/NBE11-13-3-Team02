package com.gachisa.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// 부분 수정 DTO: 필드가 null이면 "변경하지 않음"을 의미하므로 @NotBlank는 붙이지 않는다
// (UserService.updateMe의 null/blank 체크와 의미가 맞아야 함)
@Schema(description = "내 정보 수정 요청. 값을 보낸 필드만 변경되고, 생략(null)한 필드는 그대로 유지됨")
public record UserUpdateRequest(
    @Schema(description = "변경할 이름. 변경하지 않으려면 생략", example = "홍길동")
    String name,
    @Schema(description = "현재 비밀번호. newPassword를 보낼 때만 필요(소셜 전용 계정이 최초 비밀번호를 설정하는 경우는 제외)")
    String currentPassword,
    @Schema(description = "새 비밀번호(4자 이상). 변경하지 않으려면 생략")
    @Size(min = 4) String newPassword
) {}
