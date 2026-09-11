package com.gachisa.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.participation.dto.ParticipationCreateRequest;
import com.gachisa.participation.entity.Participation;
import com.gachisa.participation.entity.ParticipationStatus;
import com.gachisa.participation.repository.ParticipationRepository;
import com.gachisa.product.entity.Product;
import com.gachisa.user.entity.User;
import com.gachisa.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ParticipationServiceTest {

    @Mock ParticipationRepository participationRepository;
    @Mock GroupBuyService groupBuyService;
    @Mock UserRepository userRepository;
    @Mock Product product;

    private ParticipationService participationService;

    @BeforeEach
    void setUp() {
        participationService = new ParticipationService(
                participationRepository, groupBuyService, userRepository);
    }

    @Test
    void repeatedParticipationReturnsExistingParticipationWithoutIncreasingCount() {
        User user = User.builder().email("buyer@test.com").name("구매자").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(product.getName()).willReturn("공동구매 상품");

        GroupBuy groupBuy = GroupBuy.builder()
                .product(product)
                .targetCount(10)
                .discountRate(new BigDecimal("0.20"))
                .openAt(LocalDateTime.now().minusHours(1))
                .deadline(LocalDateTime.now().plusDays(1))
                .sellerId(2L)
                .build();
        ReflectionTestUtils.setField(groupBuy, "id", 3L);
        groupBuy.reserve(6);

        Participation existing = Participation.builder()
                .groupBuy(groupBuy)
                .user(user)
                .quantity(1)
                .build();
        ReflectionTestUtils.setField(existing, "id", 4L);

        ParticipationCreateRequest request = new ParticipationCreateRequest();
        ReflectionTestUtils.setField(request, "quantity", 1);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(groupBuyService.reserveSlots(3L, 1)).willReturn(groupBuy);
        given(participationRepository.findFirstByGroupBuy_IdAndUser_IdAndStatusInOrderByIdDesc(
                3L, 1L, List.of(ParticipationStatus.PARTICIPATING, ParticipationStatus.CONFIRMED)))
                .willReturn(Optional.of(existing));

        var response = participationService.participate(3L, 1L, request);

        assertThat(response.getParticipationId()).isEqualTo(4L);
        verify(groupBuyService).releaseSlots(3L, 1);
        verify(participationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refundBeforeDeadlineReturnsReservedSlot() {
        GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
        Participation participation = org.mockito.Mockito.mock(Participation.class);
        given(participationRepository.findById(4L)).willReturn(Optional.of(participation));
        given(participation.getStatus()).willReturn(ParticipationStatus.CONFIRMED);
        given(participation.getGroupBuy()).willReturn(groupBuy);
        given(participation.getQuantity()).willReturn(1);
        given(groupBuy.getId()).willReturn(3L);
        given(groupBuy.getStatus()).willReturn(GroupBuyStatus.RECRUITING);
        given(groupBuy.isDeadlinePassed(org.mockito.ArgumentMatchers.any())).willReturn(false);

        participationService.refundPayment(4L);

        verify(groupBuyService).releaseSlots(3L, 1);
        verify(participation).refund();
    }

    @Test
    void participationCannotBeCancelledAfterDeadline() {
        GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
        Participation participation = org.mockito.Mockito.mock(Participation.class);
        given(participationRepository.findByIdAndUser_Id(4L, 1L)).willReturn(Optional.of(participation));
        given(participation.isCancelable()).willReturn(true);
        given(participation.getGroupBuy()).willReturn(groupBuy);
        given(groupBuy.getStatus()).willReturn(GroupBuyStatus.RECRUITING);
        given(groupBuy.isDeadlinePassed(org.mockito.ArgumentMatchers.any())).willReturn(true);

        assertThatThrownBy(() -> participationService.cancel(4L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PARTICIPATION_NOT_CANCELABLE);

        verify(groupBuyService, never()).releaseSlots(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

}
