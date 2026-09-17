package com.gachisa.payment.service

import com.gachisa.groupbuy.dto.GroupBuyPaymentInfo
import com.gachisa.groupbuy.service.GroupBuyService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.product.dto.ProductPaymentInfo
import com.gachisa.product.service.ProductService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class PaymentAmountCalculatorTest {
    @Mock private lateinit var groupBuyService: GroupBuyService
    @Mock private lateinit var productService: ProductService
    private lateinit var calculator: PaymentAmountCalculator

    @BeforeEach
    fun setUp() { calculator = PaymentAmountCalculator(groupBuyService, productService) }

    @Test
    fun calculatesDiscountedAmountWithQuantity() {
        val participation = ParticipationPaymentInfo(1L, 10L, 20L, 2, true)
        given(groupBuyService.getPaymentInfo(20L)).willReturn(GroupBuyPaymentInfo(20L, 30L, BigDecimal("0.30")))
        given(productService.getPaymentInfo(30L)).willReturn(ProductPaymentInfo(30L, 18_000))
        assertThat(calculator.calculate(participation)).isEqualTo(25_200)
    }
}
