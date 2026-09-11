package com.gachisa.concurrency.controller;

import com.gachisa.concurrency.dto.ConcurrencyStressRequest;
import com.gachisa.concurrency.dto.ConcurrencyStressResponse;
import com.gachisa.concurrency.dto.GroupBuyListItem;
import com.gachisa.concurrency.service.ConcurrencyDemoService;
import com.gachisa.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * local 프로필에서만 활성화되는 동시성 검증 API.
 * 프론트 데모 페이지(/dev/concurrency)에서 호출한다.
 */
@Tag(name = "Concurrency-Demo", description = "동시성 제어(락/Redis) 데모용 API. local 프로필에서만 활성화됩니다. 관리자/판매자 전용.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@Profile("local")
@RequiredArgsConstructor
public class ConcurrencyDemoController {

    private final ConcurrencyDemoService concurrencyDemoService;

    /** 데모 페이지에서 groupBuyId를 드롭다운으로 고를 수 있도록 DB에 있는 목록을 내려준다. */
    @Operation(summary = "데모용 공동구매 목록 조회")
    @GetMapping("/api/dev/group-buys")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ApiResponse<List<GroupBuyListItem>> list() {
        List<GroupBuyListItem> items = concurrencyDemoService.listGroupBuys();
        return ApiResponse.ok("공동구매 목록 조회 성공", items);
    }

    @Operation(summary = "동시성 부하 테스트 실행", description = "지정한 개수만큼 동시 참여 요청을 발생시켜 동시성 제어 방식별 결과를 비교합니다.")
    @PostMapping("/api/dev/group-buys/{groupBuyId}/concurrency-stress")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ApiResponse<ConcurrencyStressResponse> stress(
        @PathVariable Long groupBuyId,
        @Valid @RequestBody ConcurrencyStressRequest request
    ) {
        ConcurrencyStressResponse response = concurrencyDemoService.run(groupBuyId, request);
        return ApiResponse.ok(response.getSummary(), response);
    }
}
