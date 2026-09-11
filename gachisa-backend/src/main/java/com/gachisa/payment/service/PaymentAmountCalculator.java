package com.gachisa.payment.service;

import com.gachisa.groupbuy.dto.GroupBuyPaymentInfo;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.product.dto.ProductPaymentInfo;
import com.gachisa.product.service.ProductService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAmountCalculator {

    private final GroupBuyService groupBuyService;
    private final ProductService productService;

    public int calculate(ParticipationPaymentInfo participation) {
        GroupBuyPaymentInfo groupBuy = groupBuyService.getPaymentInfo(participation.groupBuyId());
        ProductPaymentInfo product = productService.getPaymentInfo(groupBuy.productId());
        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(groupBuy.discountRate());

        return BigDecimal.valueOf(product.basePrice())
                .multiply(discountMultiplier)
                .multiply(BigDecimal.valueOf(participation.quantity()))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
