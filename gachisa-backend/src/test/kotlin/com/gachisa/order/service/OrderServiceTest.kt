package com.gachisa.order.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.groupbuy.dto.GroupBuyPaymentInfo
import com.gachisa.groupbuy.service.GroupBuyService
import com.gachisa.order.dto.DeliveryAddressRequest
import com.gachisa.order.dto.OrderCreateCommand
import com.gachisa.order.entity.DeliveryStatus
import com.gachisa.order.entity.Order
import com.gachisa.order.repository.OrderRepository
import com.gachisa.product.dto.ProductResponse
import com.gachisa.product.service.ProductService
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class OrderServiceTest {

    private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 18, 12, 0)

    @Mock lateinit var orderRepository: OrderRepository
    @Mock lateinit var timeProvider: TimeProvider
    @Mock lateinit var groupBuyService: GroupBuyService
    @Mock lateinit var productService: ProductService
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderService = OrderService(orderRepository, timeProvider, groupBuyService, productService)
    }

    @Test
    fun createsWaitingOrderAfterPaymentCompletion() {
        val command = OrderCreateCommand(10L, 20L, 30L, 40L, 2, 12_600)
        given(orderRepository.findByParticipationId(10L)).willReturn(Optional.empty())
        given(groupBuyService.getPaymentInfo(40L))
                .willReturn(GroupBuyPaymentInfo(40L, 50L, BigDecimal("0.20")))
        given(productService.getProduct(50L)).willReturn(productResponse())
        given(timeProvider.now()).willReturn(NOW)
        given(orderRepository.save(org.mockito.ArgumentMatchers.any(Order::class.java)))
                .willAnswer { invocation ->
                    val savedOrder = invocation.getArgument<Order>(0)
                    ReflectionTestUtils.setField(savedOrder, "id", 1L)
                    savedOrder
                }

        val response = orderService.createOrderIfAbsent(command)

        assertThat(response.participationId).isEqualTo(10L)
        assertThat(response.paymentId).isEqualTo(20L)
        assertThat(response.orderNumber).matches("\\d{9}")
        assertThat(response.productId).isEqualTo(50L)
        assertThat(response.productName).isEqualTo("공동구매 상품")
        assertThat(response.quantity).isEqualTo(2)
        assertThat(response.basePrice).isEqualTo(7_875)
        assertThat(response.discountRate).isEqualByComparingTo("0.20")
        assertThat(response.discountAmount).isEqualTo(3_150)
        assertThat(response.amount).isEqualTo(12_600)
        assertThat(response.groupBuyId).isEqualTo(40L)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.WAITING_FOR_GROUP_BUY)
    }

    @Test
    fun returnsExistingOrderForDuplicatePaymentCompletion() {
        val existingOrder = order(1L, DeliveryStatus.PREPARING)
        given(orderRepository.findByParticipationId(10L)).willReturn(Optional.of(existingOrder))

        val response = orderService.createOrderIfAbsent(
                OrderCreateCommand(10L, 20L, 30L, 40L, 2, 12_600))

        assertThat(response.orderId).isEqualTo(1L)
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any(Order::class.java))
    }

    @Test
    fun buyerCanReadOwnOrders() {
        val order = order(1L, DeliveryStatus.PREPARING)
        given(orderRepository.findAllByBuyerId(
                30L,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))))
                .willReturn(PageImpl(listOf(order)))

        val response = orderService.getMyOrders(30L, 0, 20)

        assertThat(response.content).hasSize(1)
        assertThat(response.content.get(0).orderId).isEqualTo(1L)
    }

    @Test
    fun registeringAddressDoesNotStartShippingBeforeGroupBuySettlement() {
        val order = order(1L, DeliveryStatus.WAITING_FOR_GROUP_BUY)
        given(orderRepository.findById(1L)).willReturn(Optional.of(order))
        given(timeProvider.now()).willReturn(NOW.plusHours(1))

        val response = orderService.registerDeliveryAddress(1L, 30L, deliveryAddress())

        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.WAITING_FOR_GROUP_BUY)
        assertThat(response.carrier).isEqualTo("자체배송")
        assertThat(response.trackingNumber).isNull()
        assertThat(response.shippingStartedAt).isNull()
        assertThat(response.expectedDeliveryAt).isNull()
    }

    @Test
    fun anotherBuyerCannotRegisterDeliveryAddress() {
        val order = order(1L, DeliveryStatus.PREPARING)
        given(orderRepository.findById(1L)).willReturn(Optional.of(order))

        assertThatThrownBy { orderService.registerDeliveryAddress(1L, 999L, deliveryAddress()) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.FORBIDDEN)
    }

    @Test
    fun registeredDeliveryAddressCannotBeOverwritten() {
        val order = order(1L, DeliveryStatus.WAITING_FOR_GROUP_BUY)
        order.registerDeliveryAddress(
                "기존 수령인", "010-1111-1111", "06234",
                "기존 주소", "101호", null, NOW)
        given(orderRepository.findById(1L)).willReturn(Optional.of(order))
        given(timeProvider.now()).willReturn(NOW)

        assertThatThrownBy { orderService.registerDeliveryAddress(1L, 30L, deliveryAddress()) }
                .isInstanceOf(CustomException::class.java)
                .extracting { exception -> (exception as CustomException).getErrorCode() }
                .isEqualTo(ErrorCode.DELIVERY_ADDRESS_ALREADY_REGISTERED)

        assertThat(order.address).isEqualTo("기존 주소")
    }

    @Test
    fun adminCanCorrectDeliveryStatus() {
        val order = order(1L, DeliveryStatus.PREPARING)
        order.registerDeliveryAddress(
                "구매자", "010-1234-5678", "06234",
                "서울특별시 강남구", "101호", null, NOW)
        given(orderRepository.findByOrderNumber("018330029")).willReturn(Optional.of(order))
        given(timeProvider.now()).willReturn(NOW.plusHours(1))

        val response = orderService.updateDeliveryStatusByAdmin("018330029", DeliveryStatus.DELIVERED)

        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.DELIVERED)
        assertThat(response.deliveredAt).isEqualTo(NOW.plusHours(1))
    }

    @Test
    fun deliveryScheduleUsesOneDayPreparationAndTwoDaysShipping() {
        given(timeProvider.now()).willReturn(NOW)
        given(orderRepository.startShippingDue(NOW.minusDays(1), NOW)).willReturn(2)
        given(orderRepository.completeDeliveriesDue(NOW.minusDays(2), NOW)).willReturn(3)

        val completedCount = orderService.completeDeliveriesDue()

        assertThat(completedCount).isEqualTo(5)
        verify(orderRepository).startShippingDue(NOW.minusDays(1), NOW)
        verify(orderRepository).completeDeliveriesDue(NOW.minusDays(2), NOW)
    }

    @Test
    fun achievedGroupBuyStartsOneDayPreparation() {
        given(timeProvider.now()).willReturn(NOW)
        given(orderRepository.startPreparationForGroupBuy(40L, NOW)).willReturn(3)

        val startedCount = orderService.startPreparationForGroupBuy(40L)

        assertThat(startedCount).isEqualTo(3)
        verify(orderRepository).startPreparationForGroupBuy(40L, NOW)
    }

    @Test
    fun refundBeforeShippingCancelsOrder() {
        val order = order(1L, DeliveryStatus.WAITING_FOR_GROUP_BUY)
        given(orderRepository.findByPaymentId(20L)).willReturn(Optional.of(order))
        given(timeProvider.now()).willReturn(NOW.plusHours(1))

        orderService.reflectRefund(20L)

        assertThat(order.deliveryStatus).isEqualTo(DeliveryStatus.CANCELLED)
    }

    @Test
    fun refundAfterShippingStartsReturn() {
        val order = order(1L, DeliveryStatus.PREPARING)
        order.registerDeliveryAddress(
                "구매자", "010-1234-5678", "06234",
                "서울특별시 강남구", "101호", null, NOW)
        order.changeDeliveryStatusByAdmin(DeliveryStatus.SHIPPING, NOW)
        given(orderRepository.findByPaymentId(20L)).willReturn(Optional.of(order))
        given(timeProvider.now()).willReturn(NOW.plusHours(1))

        orderService.reflectRefund(20L)

        assertThat(order.deliveryStatus).isEqualTo(DeliveryStatus.RETURNING)
    }

    private fun deliveryAddress(): DeliveryAddressRequest {
        return DeliveryAddressRequest(
                "구매자", "010-1234-5678", "06234",
                "서울특별시 강남구", "101호", "문 앞에 놓아주세요"
        )
    }

    private fun order(id: Long, status: DeliveryStatus): Order {
        val order = Order(
                orderNumber = "018330029",
                participationId = 10L,
                paymentId = 20L,
                buyerId = 30L,
                groupBuyId = 40L,
                productId = 50L,
                productName = "공동구매 상품",
                productImageUrl = null,
                quantity = 2,
                basePrice = 7_875,
                discountRate = BigDecimal("0.20"),
                discountAmount = 3_150,
                amount = 12_600,
                deliveryStatus = status,
                createdAt = NOW,
                updatedAt = NOW
        )
        ReflectionTestUtils.setField(order, "id", id)
        return order
    }

    private fun productResponse(): ProductResponse {
        return ProductResponse(
                50L, 60L, "판매자", 70L, "생활", "공동구매 상품",
                "상품 설명", 7_875, 100, null, "ON_SALE", NOW
        )
    }
}
