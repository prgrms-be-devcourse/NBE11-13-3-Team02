package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.gachisa.groupbuy.dto.GroupBuyPaymentInfo;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.product.dto.ProductPaymentInfo;
import com.gachisa.product.service.ProductService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentAmountCalculatorTest {

    @Mock GroupBuyService groupBuyService;
    @Mock ProductService productService;
    private PaymentAmountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PaymentAmountCalculator(groupBuyService, productService);
    }

    @Test
    void calculatesDiscountedAmountWithQuantity() {
        ParticipationPaymentInfo participation =
                new ParticipationPaymentInfo(1L, 10L, 20L, 2, true);
        given(groupBuyService.getPaymentInfo(20L))
                .willReturn(new GroupBuyPaymentInfo(20L, 30L, new BigDecimal("0.30")));
        given(productService.getPaymentInfo(30L))
                .willReturn(new ProductPaymentInfo(30L, 18_000));

        int amount = calculator.calculate(participation);

        assertThat(amount).isEqualTo(25_200);
    }
}
