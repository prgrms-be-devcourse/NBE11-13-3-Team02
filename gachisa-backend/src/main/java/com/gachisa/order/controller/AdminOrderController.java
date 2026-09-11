package com.gachisa.order.controller;

import com.gachisa.order.dto.DeliveryResponse;
import com.gachisa.order.dto.DeliveryStatusUpdateRequest;
import com.gachisa.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order-Admin", description = "주문 배송 상태 관리 (관리자 전용)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "배송 상태 변경 (관리자 전용)", description = "SHIPPING/DELIVERED/RETURNING/RETURNED로 변경하려면 " +
            "해당 주문에 배송지가 먼저 등록되어 있어야 합니다.")
    @PatchMapping("/{orderNumber}/delivery-status")
    public DeliveryResponse updateDeliveryStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody DeliveryStatusUpdateRequest request
    ) {
        return orderService.updateDeliveryStatusByAdmin(orderNumber, request.deliveryStatus());
    }
}
