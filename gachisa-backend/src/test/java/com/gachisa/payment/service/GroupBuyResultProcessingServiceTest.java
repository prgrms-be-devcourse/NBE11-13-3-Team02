package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.payment.dto.GroupBuyResultCommand;
import com.gachisa.payment.entity.Payment;
import com.gachisa.payment.repository.PaymentRepository;
import com.gachisa.payment.entity.PaymentStatus;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.participation.service.ParticipationService;
import com.gachisa.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupBuyResultProcessingServiceTest {

    private static final String REFUND_REASON = "공동구매 목표 인원 미달";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundService refundService;

    @Mock
    private ParticipationService participationService;

    @Mock
    private OrderService orderService;

    private GroupBuyResultProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new GroupBuyResultProcessingService(
                paymentRepository, refundService, participationService, orderService);
    }

    @Test
    void failedGroupBuyRefundsEachPaymentAndContinuesAfterOneFailure() {
        Payment first = payment(1L);
        Payment second = payment(2L);
        GroupBuyResultCommand command = new GroupBuyResultCommand(
                10L,
                GroupBuyResultCommand.Result.FAILED,
                List.of(101L, 102L)
        );
        given(participationService.getPaymentInfo(101L)).willReturn(participationInfo(101L, 10L));
        given(participationService.getPaymentInfo(102L)).willReturn(participationInfo(102L, 10L));
        given(paymentRepository.findAllByParticipationIdInAndStatus(
                command.participationIds(), PaymentStatus.PAID))
                .willReturn(List.of(first, second));
        given(refundService.requestRefund(1L, REFUND_REASON))
                .willThrow(new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE));

        processingService.process(command);

        verify(refundService).requestRefund(1L, REFUND_REASON);
        verify(refundService).requestRefund(2L, REFUND_REASON);
    }

    @Test
    void achievedGroupBuyDoesNotRequestRefund() {
        Payment payment = payment(1L);
        GroupBuyResultCommand command = new GroupBuyResultCommand(
                10L,
                GroupBuyResultCommand.Result.ACHIEVED,
                List.of(101L)
        );
        given(participationService.getPaymentInfo(101L)).willReturn(participationInfo(101L, 10L));
        given(paymentRepository.findAllByParticipationIdInAndStatus(
                command.participationIds(), PaymentStatus.PAID))
                .willReturn(List.of(payment));

        processingService.process(command);

        verify(orderService).startPreparationForGroupBuy(10L);
        verify(refundService, never()).requestRefund(1L, REFUND_REASON);
    }

    @Test
    void rejectsParticipationFromDifferentGroupBuy() {
        GroupBuyResultCommand command = new GroupBuyResultCommand(
                10L,
                GroupBuyResultCommand.Result.FAILED,
                List.of(101L)
        );
        given(participationService.getPaymentInfo(101L)).willReturn(participationInfo(101L, 99L));

        assertThatThrownBy(() -> processingService.process(command))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(refundService, never()).requestRefund(1L, REFUND_REASON);
    }

    @Test
    void rejectsDuplicateParticipationIds() {
        GroupBuyResultCommand command = new GroupBuyResultCommand(
                10L,
                GroupBuyResultCommand.Result.FAILED,
                List.of(101L, 101L)
        );

        assertThatThrownBy(() -> processingService.process(command))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private Payment payment(Long paymentId) {
        Payment payment = Payment.builder().status(PaymentStatus.PAID).build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private ParticipationPaymentInfo participationInfo(Long participationId, Long groupBuyId) {
        return new ParticipationPaymentInfo(participationId, 1L, groupBuyId, 1, true);
    }

}
