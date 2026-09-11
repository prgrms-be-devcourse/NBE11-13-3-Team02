package com.gachisa.product.dto;

import com.gachisa.product.entity.Product;
import java.time.LocalDateTime;

public record ProductResponse(
    Long id,
    Long sellerId,
    String sellerName,
    Long categoryId,
    String categoryName,
    String name,
    String description,
    int basePrice,
    int stock,
    String imageUrl,
    String status,
    LocalDateTime createdAt
) {
    public static ProductResponse of(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getSeller().getId(),
            product.getSeller().getName(),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.getName(),
            product.getDescription(),
            product.getBasePrice(),
            product.getStock(),
            product.getImageUrl(),
            product.getStatus().name(),
            product.getCreatedAt()
        );
    }
}
