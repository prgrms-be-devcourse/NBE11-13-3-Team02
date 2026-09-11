package com.gachisa.groupbuy.controller;

import com.gachisa.global.response.ApiResponse;
import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.groupbuy.dto.GroupBuyCreateRequest;
import com.gachisa.groupbuy.dto.GroupBuyDetailResponse;
import com.gachisa.groupbuy.dto.GroupBuyResponse;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.service.GroupBuyService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "GroupBuy", description = "공동구매 생성/조회/검색/취소. 생성/취소는 판매자(또는 관리자) 전용, 조회/검색은 인증 불필요.")
@RestController
@RequestMapping("/api/group-buys")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    /** GB-01. 판매자만 생성 가능 */
    @Operation(summary = "공동구매 생성 (판매자 전용)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<GroupBuyResponse>> create(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody GroupBuyCreateRequest request
    ) {
        GroupBuyResponse response = groupBuyService.createGroupBuy(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("공동구매가 생성되었습니다.", response));
    }

    /** GB-02. 인증 불필요 - 필터 없는 기본 목록 */
    @Operation(summary = "공동구매 목록 조회", description = "인증 불필요. status로 필터링 가능(생략 시 전체).")
    @GetMapping
    public ApiResponse<Page<GroupBuyResponse>> list(
        @Parameter(description = "공동구매 상태 필터") @RequestParam(required = false) GroupBuyStatus status,
        @Parameter(description = "페이지 번호(0부터)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<GroupBuyResponse> result = groupBuyService.getGroupBuyList(status, pageable);
        return ApiResponse.ok("공동구매 목록을 조회했습니다.", result);
    }

    /**
     * 검색 전용 엔드포인트.
     * 가격 필터/정렬은 할인가(basePrice - basePrice*discountRate) 기준으로 DB에서 직접 계산한다.
     */
    @Operation(summary = "공동구매 검색", description = "인증 불필요. 가격 필터/정렬은 할인가(정가-할인율) 기준입니다. " +
            "sort는 'price_asc'/'price_desc'/그 외(기본값, 마감임박순).")
    @GetMapping("/search")
    public ApiResponse<Page<GroupBuyResponse>> search(
        @Parameter(description = "공동구매 상태 필터") @RequestParam(required = false) GroupBuyStatus status,
        @Parameter(description = "상품명 검색어") @RequestParam(required = false) String keyword,
        @Parameter(description = "카테고리 ID") @RequestParam(required = false) Long categoryId,
        @Parameter(description = "최소 할인가") @RequestParam(required = false) Integer minPrice,
        @Parameter(description = "최대 할인가") @RequestParam(required = false) Integer maxPrice,
        @Parameter(description = "정렬 기준: price_asc/price_desc/기본값(마감임박순)") @RequestParam(required = false) String sort,
        @Parameter(description = "페이지 번호(0부터)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<GroupBuyResponse> result = groupBuyService.searchGroupBuy(
            status, keyword, categoryId, minPrice, maxPrice, sort, pageable);
        return ApiResponse.ok("공동구매 검색 결과를 조회했습니다.", result);
    }

    /** GB-03. 인증 불필요 */
    @Operation(summary = "공동구매 상세 조회", description = "인증 불필요.")
    @GetMapping("/{groupBuyId}")
    public ApiResponse<GroupBuyDetailResponse> detail(@PathVariable Long groupBuyId) {
        GroupBuyDetailResponse response = groupBuyService.getGroupBuyDetail(groupBuyId);
        return ApiResponse.ok("공동구매 상세를 조회했습니다.", response);
    }

    /** GB-05. 판매자 본인 소유는 취소 가능, 관리자는 아무 공동구매나 취소 가능(운영 목적) */
    @Operation(summary = "공동구매 취소", description = "판매자는 본인 소유 공동구매만, 관리자는 모든 공동구매를 취소할 수 있습니다. " +
            "모집중(RECRUITING) 상태에서만 취소 가능합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{groupBuyId}/cancel")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ApiResponse<GroupBuyResponse> cancel(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long groupBuyId
    ) {
        boolean isAdmin = "ADMIN".equals(userDetails.getRole());
        GroupBuyResponse response =
            groupBuyService.cancelGroupBuy(userDetails.getUserId(), groupBuyId, isAdmin);
        return ApiResponse.ok("공동구매가 취소되었습니다.", response);
    }
}
