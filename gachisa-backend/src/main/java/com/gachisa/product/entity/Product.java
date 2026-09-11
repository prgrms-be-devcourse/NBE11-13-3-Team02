package com.gachisa.product.entity;

import com.gachisa.category.entity.Category;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Lob
    private String description;

    @Column(nullable = false)
    private int basePrice;

    @Column(nullable = false)
    private int stock;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Product(User seller, Category category, String name, String description, int basePrice, int stock,
                    String imageUrl, ProductStatus status, LocalDateTime createdAt) {
        this.seller = seller;
        this.category = category;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void stopSale() {
        this.status = ProductStatus.SUSPENDED;
    }

    public void resumeSale() {
        this.status = ProductStatus.ON_SALE;
    }

    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public boolean isOwnedBy(Long userId) {
        return this.seller.getId().equals(userId);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateBasePrice(int basePrice) {
        this.basePrice = basePrice;
    }

    public void updateStock(int stock) {
        this.stock = stock;
    }

    public void increaseStock(int quantity) {
        this.stock += quantity;
    }

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }

    public void updateCategory(Category category) {
        this.category = category;
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
