package com.gachisa.payment.service

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.participation.service.ParticipationService
import com.gachisa.payment.dto.PaymentCancellationResponse
import com.gachisa.payment.dto.RefundResponse
import com.gachisa.payment.entity.PaymentAttemptStatus
import com.gachisa.payment.entity.PaymentStatus
import com.gachisa.payment.repository.PaymentAttemptRepository
import com.gachisa.payment.repository.PaymentRepository
import com.gachisa.payment.repository.RefundRepository
import com.gachisa.queue.service.QueueService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentCancellationService(
    private val paymentRepository: PaymentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val refundRepository: RefundRepository,
    private val participationService: ParticipationService,
    private val refundService: RefundService,
    private val queueService: QueueService,
    private val timeProvider: TimeProvider,
) {
    companion object {
        private const val USER_CANCELLATION_REASON = "구매자 공동구매 참여 취소"
    }

    @Transactional
    fun cancel(participationId: Long, userId: Long): PaymentCancellationResponse {
        val participation = participationService.getPaymentInfo(participationId)
        if (participation.userId() != userId) throw CustomException(ErrorCode.FORBIDDEN)

        val payment = paymentRepository.findByParticipationIdForUpdate(participationId).orElse(null)
        if (payment == null || payment.status == PaymentStatus.READY) {
            if (payment != null) {
                val activeAttempt = paymentAttemptRepository
                    .findFirstByPaymentIdAndStatusInOrderByCreatedAtDesc(
                        payment.id!!,
                        listOf(PaymentAttemptStatus.READY, PaymentAttemptStatus.PROCESSING),
                    ).orElse(null)
                if (activeAttempt != null && activeAttempt.status == PaymentAttemptStatus.READY) {
                    activeAttempt.cancel(timeProvider.now())
                }
            }
            participationService.cancel(participationId, userId)
            queueService.completeAdmission(participation.groupBuyId(), userId)
            return PaymentCancellationResponse.cancelled()
        }
        if (payment.status == PaymentStatus.PAID) {
            return PaymentCancellationResponse.refundRequested(
                refundService.requestRefund(payment.id!!, USER_CANCELLATION_REASON),
            )
        }
        return PaymentCancellationResponse.refundRequested(getRefund(payment.id!!))
    }

    fun getRefundStatus(participationId: Long, userId: Long): RefundResponse {
        val participation = participationService.getPaymentInfo(participationId)
        if (participation.userId() != userId) throw CustomException(ErrorCode.FORBIDDEN)
        val payment = paymentRepository.findByParticipationId(participationId)
            .orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }
        return getRefund(payment.id!!)
    }

    private fun getRefund(paymentId: Long): RefundResponse = refundRepository.findByPaymentId(paymentId)
        .map(RefundResponse::from)
        .orElseThrow { CustomException(ErrorCode.REFUND_NOT_FOUND) }
}
