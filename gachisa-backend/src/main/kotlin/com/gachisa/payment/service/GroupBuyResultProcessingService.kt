package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.order.service.OrderService
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.dto.GroupBuyResultCommand
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GroupBuyResultProcessingService(
    private val paymentRepository: PaymentRepository,
    private val refundService: RefundService,
    private val participationService: ParticipationService,
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(GroupBuyResultProcessingService::class.java)

    companion object {
        private const val TARGET_NOT_ACHIEVED_REASON = "공동구매 목표 인원 미달"
    }

    fun process(command: GroupBuyResultCommand) {
        validateParticipations(command)
        val payments = paymentRepository.findAllByParticipationIdInAndStatus(
            command.participationIds,
            PaymentStatus.PAID,
        )
        if (command.result == GroupBuyResultCommand.Result.ACHIEVED) {
            orderService.startPreparationForGroupBuy(command.groupBuyId)
            return
        }

        for (payment in payments) {
            try {
                refundService.requestRefund(payment.id!!, TARGET_NOT_ACHIEVED_REASON)
            } catch (exception: CustomException) {
                log.warn(
                    "공동구매 결과 환불 요청 실패. groupBuyId={}, paymentId={}, errorCode={}",
                    command.groupBuyId,
                    payment.id,
                    exception.getErrorCode(),
                )
            }
        }
    }

    private fun validateParticipations(command: GroupBuyResultCommand) {
        if (command.participationIds.toSet().size != command.participationIds.size) {
            throw CustomException(ErrorCode.INVALID_REQUEST)
        }

        for (participationId in command.participationIds) {
            val participation = participationService.getPaymentInfo(participationId)
            if (command.groupBuyId != participation.groupBuyId()) {
                throw CustomException(ErrorCode.INVALID_REQUEST)
            }
        }
    }
}
