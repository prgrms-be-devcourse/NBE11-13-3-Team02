package com.gachisa.groupbuy.dto;

import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.product.entity.Product;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
public class GroupBuyResponse {

    private final Long groupBuyId;
    private final Long productId;
    private final Long categoryId;
    private final String categoryName;
    private final String productName;
    private final Integer basePrice;
    private final Integer discountedPrice;
    private final String imageUrl;
    private final BigDecimal discountRate;
    private final Integer currentCount;
    private final Integer targetCount;
    private final LocalDateTime deadline;
    private final String status;

    private GroupBuyResponse(GroupBuy g) {
        Product product = g.getProduct();

        this.groupBuyId = g.getId();
        this.productId = product.getId();
        this.categoryId = product.getCategory().getId();
        this.categoryName = product.getCategory().getName();
        this.productName = product.getName();
        this.basePrice = product.getBasePrice();
        this.discountedPrice = calculateDiscountedPrice(product.getBasePrice(), g.getDiscountRate());
        this.imageUrl = product.getImageUrl();
        this.discountRate = g.getDiscountRate();
        this.currentCount = g.getCurrentCount();
        this.targetCount = g.getTargetCount();
        this.deadline = g.getDeadline();
        this.status = g.getStatus().getLabel();
    }

    public static GroupBuyResponse from(GroupBuy groupBuy) {
        return new GroupBuyResponse(groupBuy);
    }

    private Integer calculateDiscountedPrice(int basePrice, BigDecimal discountRate) {
        return BigDecimal.valueOf(basePrice)
                .multiply(BigDecimal.ONE.subtract(discountRate))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
    }
}
