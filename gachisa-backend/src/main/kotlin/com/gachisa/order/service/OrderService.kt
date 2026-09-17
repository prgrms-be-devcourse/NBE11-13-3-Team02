package com.gachisa.order.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.groupbuy.service.GroupBuyService
import com.gachisa.order.dto.DeliveryAddressRequest
import com.gachisa.order.dto.DeliveryResponse
import com.gachisa.order.dto.OrderCreateCommand
import com.gachisa.order.dto.OrderListResponse
import com.gachisa.order.dto.OrderResponse
import com.gachisa.order.entity.DeliveryStatus
import com.gachisa.order.entity.Order
import com.gachisa.order.repository.OrderRepository
import com.gachisa.product.service.ProductService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ThreadLocalRandom

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val timeProvider: TimeProvider,
    private val groupBuyService: GroupBuyService,
    private val productService: ProductService,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    @Transactional
    fun createOrderIfAbsent(command: OrderCreateCommand): OrderResponse {
        val existingOrder = orderRepository.findByParticipationId(command.participationId).orElse(null)
        if (existingOrder != null) return OrderResponse.from(existingOrder)

        val groupBuy = groupBuyService.getPaymentInfo(command.groupBuyId)
        val product = productService.getProduct(groupBuy.productId())
        val originalAmount = Math.multiplyExact(product.basePrice, command.quantity)
        val discountAmount = originalAmount - command.amount
        if (discountAmount < 0) throw CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)

        val now = timeProvider.now()
        val order = Order(
            orderNumber = createOrderNumber(),
            participationId = command.participationId,
            paymentId = command.paymentId,
            buyerId = command.buyerId,
            groupBuyId = command.groupBuyId,
            productId = product.id!!,
            productName = product.name,
            productImageUrl = product.imageUrl,
            quantity = command.quantity,
            basePrice = product.basePrice,
            discountRate = groupBuy.discountRate(),
            discountAmount = discountAmount,
            amount = command.amount,
            deliveryStatus = DeliveryStatus.WAITING_FOR_GROUP_BUY,
            createdAt = now,
            updatedAt = now,
        )
        return OrderResponse.from(orderRepository.save(order))
    }

    @Transactional(readOnly = true)
    fun getMyOrders(buyerId: Long, page: Int, size: Int): OrderListResponse {
        val pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"),
        )
        return OrderListResponse.from(orderRepository.findAllByBuyerId(buyerId, pageable))
    }

    @Transactional(readOnly = true)
    fun getMyOrder(orderId: Long, buyerId: Long): OrderResponse {
        val order = getOrder(orderId)
        validateBuyer(order, buyerId)
        return OrderResponse.from(order)
    }

    @Transactional(readOnly = true)
    fun getMyOrderByParticipation(participationId: Long, buyerId: Long): OrderResponse {
        val order = orderRepository.findByParticipationId(participationId)
            .orElseThrow { CustomException(ErrorCode.ORDER_NOT_FOUND) }
        validateBuyer(order, buyerId)
        return OrderResponse.from(order)
    }

    @Transactional(readOnly = true)
    fun getOrderIdByParticipationId(participationId: Long): Long? =
        orderRepository.findByParticipationId(participationId).map { it.id }.orElse(null)

    @Transactional
    fun registerDeliveryAddress(orderId: Long, buyerId: Long, request: DeliveryAddressRequest): DeliveryResponse {
        val order = getOrder(orderId)
        validateBuyer(order, buyerId)
        order.registerDeliveryAddress(
            request.recipientName,
            request.recipientPhone,
            request.zipCode,
            request.address,
            request.addressDetail,
            request.deliveryRequest,
            timeProvider.now(),
        )
        return DeliveryResponse.from(order)
    }

    @Transactional(readOnly = true)
    fun getMyDelivery(orderId: Long, buyerId: Long): DeliveryResponse {
        val order = getOrder(orderId)
        validateBuyer(order, buyerId)
        return DeliveryResponse.from(order)
    }

    @Transactional
    fun updateDeliveryStatusByAdmin(orderNumber: String, deliveryStatus: DeliveryStatus): DeliveryResponse {
        val order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow { CustomException(ErrorCode.ORDER_NOT_FOUND) }
        order.changeDeliveryStatusByAdmin(deliveryStatus, timeProvider.now())
        return DeliveryResponse.from(order)
    }

    @Transactional
    fun completeDeliveriesDue(): Int {
        val now = timeProvider.now()
        val shippingCount = orderRepository.startShippingDue(now.minusDays(1), now)
        val deliveredCount = orderRepository.completeDeliveriesDue(now.minusDays(2), now)
        return shippingCount + deliveredCount
    }

    @Transactional
    fun startPreparationForGroupBuy(groupBuyId: Long): Int =
        orderRepository.startPreparationForGroupBuy(groupBuyId, timeProvider.now())

    @Transactional
    fun reflectRefund(paymentId: Long) {
        orderRepository.findByPaymentId(paymentId).ifPresent { it.reflectRefund(timeProvider.now()) }
    }

    private fun getOrder(orderId: Long): Order = orderRepository.findById(orderId)
        .orElseThrow { CustomException(ErrorCode.ORDER_NOT_FOUND) }

    private fun validateBuyer(order: Order, buyerId: Long) {
        if (order.buyerId != buyerId) throw CustomException(ErrorCode.FORBIDDEN)
    }

    private fun createOrderNumber(): String {
        var orderNumber: String
        do {
            orderNumber = String.format("%09d", ThreadLocalRandom.current().nextInt(1, 1_000_000_000))
        } while (orderRepository.existsByOrderNumber(orderNumber))
        return orderNumber
    }
}
