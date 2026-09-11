package com.gachisa.product.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    ON_SALE("판매중"),
    SUSPENDED("판매중지");   // Soft Delete

    private final String label;
}
