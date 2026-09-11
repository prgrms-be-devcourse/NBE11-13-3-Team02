package com.gachisa.groupbuy.dto;

import java.math.BigDecimal;

public record GroupBuyPaymentInfo(
        Long groupBuyId,
        Long productId,
        BigDecimal discountRate
) {
}
