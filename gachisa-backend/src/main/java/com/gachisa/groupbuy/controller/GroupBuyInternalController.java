package com.gachisa.groupbuy.controller;

import com.gachisa.global.response.ApiResponse;
import com.gachisa.groupbuy.service.GroupBuySettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영/디버깅용 수동 정산 트리거.
 * 평소엔 GroupBuySettlementScheduler가 자동으로 처리하므로, 이 엔드포인트는
 * 데모/테스트 시 마감을 기다리지 않고 즉시 정산 결과를 보여주기 위한 용도.
 *
 * TODO(인증 담당자): SecurityConfig에서 /api/internal/** 는 ADMIN 권한만 접근 가능하도록 제한
 */
@Tag(name = "GroupBuy-Internal", description = "운영/데모용 수동 정산 트리거. 평소엔 스케줄러가 자동 처리합니다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/internal/group-buys")
@RequiredArgsConstructor
public class GroupBuyInternalController {

    private final GroupBuySettlementService settlementService;

    @Operation(summary = "공동구매 즉시 정산", description = "마감시각을 기다리지 않고 즉시 정산을 실행합니다(데모/테스트용).")
    @PatchMapping("/{groupBuyId}/settle")
    public ApiResponse<Void> settleNow(@PathVariable Long groupBuyId) {
        settlementService.settleOne(groupBuyId);
        return ApiResponse.ok("정산 처리가 실행되었습니다.", null);
    }
}
