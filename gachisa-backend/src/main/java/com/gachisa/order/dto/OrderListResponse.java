package com.gachisa.order.dto;

import com.gachisa.order.entity.Order;
import java.util.List;
import org.springframework.data.domain.Page;

public record OrderListResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static OrderListResponse from(Page<Order> orders) {
        return new OrderListResponse(
                orders.getContent().stream().map(OrderResponse::from).toList(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }
}
