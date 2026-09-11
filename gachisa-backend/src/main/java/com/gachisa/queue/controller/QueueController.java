package com.gachisa.queue.controller;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.queue.dto.QueueStatusResponse;
import com.gachisa.queue.dto.QueueTokenResponse;
import com.gachisa.queue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Queue", description = "결제 대기열 토큰 발급/상태 조회. 결제 폭주 시 순서를 보장하기 위한 대기열입니다. 모두 로그인 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/group-buys/{groupBuyId}/queue-token")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @Operation(summary = "대기열 토큰 발급", description = "결제를 시작하기 전 대기열에 등록하고 토큰을 발급받습니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QueueTokenResponse issueToken(
            @PathVariable Long groupBuyId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return queueService.issueToken(groupBuyId, requireUserId(userId));
    }

    @Operation(summary = "대기열 상태 조회", description = "발급받은 토큰으로 현재 대기 순번/입장 가능 여부를 확인합니다.")
    @GetMapping("/{queueToken}/status")
    public QueueStatusResponse getStatus(
            @PathVariable Long groupBuyId,
            @PathVariable String queueToken,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return queueService.getStatus(groupBuyId, requireUserId(userId), queueToken);
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return userId;
    }
}
