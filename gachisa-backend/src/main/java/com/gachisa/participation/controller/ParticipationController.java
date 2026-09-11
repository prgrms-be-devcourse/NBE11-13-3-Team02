package com.gachisa.participation.controller;

import com.gachisa.global.response.ApiResponse;
import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.participation.dto.ParticipationCountResponse;
import com.gachisa.participation.dto.ParticipationCreateRequest;
import com.gachisa.participation.dto.ParticipationResponse;
import com.gachisa.participation.entity.ParticipationStatus;
import com.gachisa.participation.service.ParticipationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Participation", description = "공동구매 참여 신청/취소, 참여 인원 조회, 내 참여 이력 조회.")
@RestController
@RequiredArgsConstructor
public class ParticipationController {

    private final ParticipationService participationService;

    /** PT-01 */
    @Operation(summary = "공동구매 참여 신청", description = "로그인 필요. 참여 후 결제를 완료해야 확정(CONFIRMED)됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/group-buys/{groupBuyId}/participations")
    public ResponseEntity<ApiResponse<ParticipationResponse>> participate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupBuyId,
            @Valid @RequestBody ParticipationCreateRequest request
    ) {
        ParticipationResponse response =
                participationService.participate(groupBuyId, userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("공동구매 참여가 완료되었습니다.", response));
    }

    /** PT-02 */
    @Operation(summary = "참여 취소", description = "로그인 필요. 결제 전 '참여중(PARTICIPATING)' 상태에서만 취소 가능합니다. " +
            "확정 이후 취소는 환불 API를 이용해야 합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/participations/{participationId}")
    public ApiResponse<ParticipationResponse> cancel(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long participationId
    ) {
        ParticipationResponse response =
                participationService.cancel(participationId, userDetails.getUserId());
        return ApiResponse.ok("참여가 취소되었습니다.", response);
    }

    /** PT-03. 인증 불필요, 프론트에서 폴링용으로 자주 호출 */
    @Operation(summary = "실시간 참여 인원 조회", description = "인증 불필요. 프론트에서 폴링용으로 자주 호출합니다.")
    @GetMapping("/api/group-buys/{groupBuyId}/participation-count")
    public ApiResponse<ParticipationCountResponse> count(@PathVariable Long groupBuyId) {
        ParticipationCountResponse response = participationService.getParticipationCount(groupBuyId);
        return ApiResponse.ok("참여 인원을 조회했습니다.", response);
    }

    /** PT-04 */
    @Operation(summary = "내 참여 이력 조회", description = "로그인 필요. status로 필터링 가능(생략 시 전체).")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/users/me/participations")
    public ApiResponse<Page<ParticipationResponse>> myParticipations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "참여 상태 필터") @RequestParam(required = false) ParticipationStatus status,
            @Parameter(description = "페이지 번호(0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ParticipationResponse> response =
                participationService.getMyParticipations(userDetails.getUserId(), status, pageable);
        return ApiResponse.ok("참여 이력을 조회했습니다.", response);
    }
}
