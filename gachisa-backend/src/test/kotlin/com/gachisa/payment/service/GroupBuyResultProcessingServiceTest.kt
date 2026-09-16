package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.order.service.OrderService
import com.gachisa.participation.dto.ParticipationPaymentInfo
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.dto.GroupBuyResultCommand
import com.gachisa.payment.entity.Payment
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class GroupBuyResultProcessingServiceTest {
    @Mock private lateinit var paymentRepository: PaymentRepository
    @Mock private lateinit var refundService: RefundService
    @Mock private lateinit var participationService: ParticipationService
    @Mock private lateinit var orderService: OrderService
    private lateinit var processingService: GroupBuyResultProcessingService
    @BeforeEach fun setUp() { processingService = GroupBuyResultProcessingService(paymentRepository, refundService, participationService, orderService) }
    @Test fun failedGroupBuyRefundsEachPaymentAndContinuesAfterOneFailure() {
        val command = GroupBuyResultCommand(10L, GroupBuyResultCommand.Result.FAILED, listOf(101L, 102L))
        given(participationService.getPaymentInfo(101L)).willReturn(info(101L, 10L)); given(participationService.getPaymentInfo(102L)).willReturn(info(102L, 10L))
        given(paymentRepository.findAllByParticipationIdInAndStatus(command.participationIds, PaymentStatus.PAID)).willReturn(listOf(payment(1L), payment(2L)))
        given(refundService.requestRefund(1L, REFUND_REASON)).willThrow(CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE))
        processingService.process(command)
        verify(refundService).requestRefund(1L, REFUND_REASON); verify(refundService).requestRefund(2L, REFUND_REASON)
    }
    @Test fun achievedGroupBuyDoesNotRequestRefund() {
        val command = GroupBuyResultCommand(10L, GroupBuyResultCommand.Result.ACHIEVED, listOf(101L))
        given(participationService.getPaymentInfo(101L)).willReturn(info(101L, 10L)); given(paymentRepository.findAllByParticipationIdInAndStatus(command.participationIds, PaymentStatus.PAID)).willReturn(listOf(payment(1L)))
        processingService.process(command)
        verify(orderService).startPreparationForGroupBuy(10L); verify(refundService, never()).requestRefund(1L, REFUND_REASON)
    }
    @Test fun rejectsParticipationFromDifferentGroupBuy() {
        val command = GroupBuyResultCommand(10L, GroupBuyResultCommand.Result.FAILED, listOf(101L)); given(participationService.getPaymentInfo(101L)).willReturn(info(101L, 99L))
        assertThatThrownBy { processingService.process(command) }.isInstanceOf(CustomException::class.java).extracting { (it as CustomException).getErrorCode() }.isEqualTo(ErrorCode.INVALID_REQUEST)
    }
    @Test fun rejectsDuplicateParticipationIds() {
        val command = GroupBuyResultCommand(10L, GroupBuyResultCommand.Result.FAILED, listOf(101L, 101L))
        assertThatThrownBy { processingService.process(command) }.isInstanceOf(CustomException::class.java).extracting { (it as CustomException).getErrorCode() }.isEqualTo(ErrorCode.INVALID_REQUEST)
    }
    private fun payment(id: Long) = Payment(1L, 1_000, PaymentStatus.PAID, NOW, NOW).also { ReflectionTestUtils.setField(it, "id", id) }
    private fun info(participationId: Long, groupBuyId: Long) = ParticipationPaymentInfo(participationId, 1L, groupBuyId, 1, true)
    companion object { private const val REFUND_REASON = "공동구매 목표 인원 미달"; private val NOW = LocalDateTime.of(2026, 8, 1, 0, 0) }
}
