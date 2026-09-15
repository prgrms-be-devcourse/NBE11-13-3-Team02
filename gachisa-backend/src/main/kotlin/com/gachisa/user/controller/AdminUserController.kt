package com.gachisa.user.controller

import com.gachisa.user.dto.UserAdminResponse
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.gachisa.user.service.AdminUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User-Admin", description = "회원 계정 관리 (관리자 전용). 구매자/판매자 계정 조회, 정지, 정지 해제, 강제 탈퇴.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(
    private val adminUserService: AdminUserService,
) {

    @Operation(summary = "회원 목록 조회", description = "이메일(부분 검색)/역할/상태로 필터링해 회원 목록을 조회합니다.")
    @GetMapping
    fun getUsers(
        @Parameter(description = "이메일 부분 검색") @RequestParam(required = false) email: String?,
        @Parameter(description = "역할 필터") @RequestParam(required = false) role: UserRole?,
        @Parameter(description = "상태 필터") @RequestParam(required = false) status: UserStatus?,
        @Parameter(description = "페이지 번호(0부터)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): Page<UserAdminResponse> {
        return adminUserService.getUsers(email, role, status, PageRequest.of(page, size))
    }

    @Operation(summary = "회원 상세 조회")
    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): UserAdminResponse {
        return adminUserService.getUser(userId)
    }

    @Operation(
        summary = "계정 정지",
        description = "구매자/판매자 계정을 정지합니다. 관리자 계정은 정지할 수 없습니다. " +
            "정지된 계정의 리프레시 토큰은 즉시 전량 폐기되어 다음 토큰 재발급부터 로그인이 차단됩니다. " +
            "이미 발급된 액세스 토큰은 만료 전까지는 유효합니다.",
    )
    @PatchMapping("/{userId}/suspend")
    fun suspendUser(@PathVariable userId: Long): UserAdminResponse {
        return adminUserService.suspendUser(userId)
    }

    @Operation(summary = "계정 정지 해제", description = "정지된 계정을 다시 활성 상태로 되돌립니다.")
    @PatchMapping("/{userId}/reinstate")
    fun reinstateUser(@PathVariable userId: Long): UserAdminResponse {
        return adminUserService.reinstateUser(userId)
    }

    @Operation(summary = "강제 탈퇴", description = "구매자/판매자 계정을 탈퇴 처리합니다. 되돌릴 수 없습니다.")
    @PatchMapping("/{userId}/withdraw")
    fun withdrawUser(@PathVariable userId: Long): UserAdminResponse {
        return adminUserService.withdrawUser(userId)
    }
}
