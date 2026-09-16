package com.gachisa.payment.service

import com.gachisa.groupbuy.service.GroupBuyService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.product.service.ProductService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class PaymentAmountCalculator(
    private val groupBuyService: GroupBuyService,
    private val productService: ProductService,
) {
    fun calculate(participation: ParticipationPaymentInfo): Int {
        val groupBuy = groupBuyService.getPaymentInfo(participation.groupBuyId())
        val product = productService.getPaymentInfo(groupBuy.productId())
        val discountMultiplier = BigDecimal.ONE.subtract(groupBuy.discountRate())

        return BigDecimal.valueOf(product.basePrice().toLong())
            .multiply(discountMultiplier)
            .multiply(BigDecimal.valueOf(participation.quantity().toLong()))
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact()
    }
}
