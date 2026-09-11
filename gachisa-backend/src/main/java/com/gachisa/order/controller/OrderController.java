package com.gachisa.order.controller;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.order.dto.DeliveryAddressRequest;
import com.gachisa.order.dto.DeliveryResponse;
import com.gachisa.order.dto.OrderListResponse;
import com.gachisa.order.dto.OrderResponse;
import com.gachisa.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order", description = "내 주문/배송 조회 및 배송지 등록. 모두 로그인 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "내 주문 목록 조회")
    @GetMapping
    public OrderListResponse getMyOrders(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Parameter(description = "페이지 번호(0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.getMyOrders(requireUserId(userId), page, size);
    }

    @Operation(summary = "내 주문 단건 조회")
    @GetMapping("/{orderId}")
    public OrderResponse getMyOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return orderService.getMyOrder(orderId, requireUserId(userId));
    }

    @Operation(summary = "참여 ID로 내 주문 조회")
    @GetMapping("/by-participation/{participationId}")
    public OrderResponse getMyOrderByParticipation(
            @PathVariable Long participationId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return orderService.getMyOrderByParticipation(participationId, requireUserId(userId));
    }

    @Operation(summary = "주문 배송지 등록", description = "WAITING_FOR_GROUP_BUY 또는 PREPARING 상태의 주문에만 등록 가능하며, " +
            "이미 등록되어 있으면 다시 등록할 수 없습니다.")
    @PostMapping("/{orderId}/delivery-address")
    public DeliveryResponse registerDeliveryAddress(
            @PathVariable Long orderId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody DeliveryAddressRequest request
    ) {
        return orderService.registerDeliveryAddress(orderId, requireUserId(userId), request);
    }

    @Operation(summary = "내 주문 배송 정보 조회")
    @GetMapping("/{orderId}/delivery")
    public DeliveryResponse getMyDelivery(
            @PathVariable Long orderId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return orderService.getMyDelivery(orderId, requireUserId(userId));
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return userId;
    }
}
