package com.gachisa.participation.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.participation.dto.ParticipationCountResponse;
import com.gachisa.participation.dto.ParticipationCreateRequest;
import com.gachisa.participation.dto.ParticipationResponse;
import com.gachisa.participation.dto.ParticipationPaymentInfo;
import com.gachisa.participation.entity.Participation;
import com.gachisa.participation.entity.ParticipationStatus;
import com.gachisa.participation.repository.ParticipationRepository;
import com.gachisa.user.entity.User;
import com.gachisa.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipationService {

    private static final List<ParticipationStatus> ACTIVE_STATUSES =
            List.of(ParticipationStatus.PARTICIPATING, ParticipationStatus.CONFIRMED);

    private final ParticipationRepository participationRepository;
    private final GroupBuyService groupBuyService;
    private final UserRepository userRepository;
    // TODO(결제 담당자): PaymentService 주입 후 participate() 안에서 결제 요청까지
    // 같은 트랜잭션으로 묶어 payment.status <-> participation.status 강한 동기화

    /**
     * PT-01. 공동구매 참여
     *
     * 동시성 제어: GroupBuyService.reserveSlots()가 비관적 락으로 group_buy row를 잠그고
     * currentCount를 증가시킨다. 이 메서드가 @Transactional이므로, reserveSlots 호출과
     * Participation 저장이 하나의 트랜잭션(하나의 락 범위) 안에서 처리된다.
     *
     * 정원 초과 시 GroupBuy.reserve()에서 CustomException(GROUP_BUY_FULL)을 던지고,
     * 트랜잭션이 롤백되어 currentCount 증가도 취소된다.
     */
    @Transactional
    public ParticipationResponse participate(Long groupBuyId, Long userId, ParticipationCreateRequest request) {
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            throw new CustomException(ErrorCode.INVALID_QUANTITY);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));

        // 1) 비관적 락으로 정원을 예약 (동시성 제어 핵심 지점)
        GroupBuy groupBuy = groupBuyService.reserveSlots(groupBuyId, request.getQuantity());

        // 같은 사용자가 새로고침·뒤로가기로 다시 요청하면 새 참여를 만들지 않는다.
        // 정원 락을 획득한 뒤 조회하므로 동시 요청도 순서대로 같은 참여를 반환한다.
        Participation existingParticipation = participationRepository
                .findFirstByGroupBuy_IdAndUser_IdAndStatusInOrderByIdDesc(
                        groupBuyId, userId, ACTIVE_STATUSES)
                .orElse(null);
        if (existingParticipation != null) {
            // Redis+DB 모두 롤백 (reserveSlots가 이미 둘 다 증가시킨 상태)
            groupBuyService.releaseSlots(groupBuyId, request.getQuantity());
            return ParticipationResponse.from(existingParticipation);
        }

        // 2) 참여 레코드 생성 (초기 상태: 참여중)
        Participation participation = Participation.builder()
                .groupBuy(groupBuy)
                .user(user)
                .quantity(request.getQuantity())
                .build();
        participationRepository.save(participation);

        // TODO(결제 연동): paymentService.requestPayment(participation) 호출 후
        // 결제 성공 시 participation.confirm() 을 같은 트랜잭션에서 호출 (강한 동기화)

        return ParticipationResponse.from(participation);
    }

    /**
     * PT-02. 참여 취소
     * "참여중" 상태에서만 가능. 확정 이후는 결제 담당 모듈의 환불 API를 사용해야 한다.
     */
    @Transactional
    public ParticipationResponse cancel(Long participationId, Long userId) {
        Participation participation = participationRepository.findByIdAndUser_Id(participationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPATION_NOT_FOUND));

        if (!participation.isCancelable()) {
            throw new CustomException(ErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }

        GroupBuy groupBuy = participation.getGroupBuy();
        if (groupBuy.getStatus() != GroupBuyStatus.RECRUITING
                || groupBuy.isDeadlinePassed(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }

        participation.cancel();
        // 예약된 인원을 원자적으로 롤백 (역시 비관적 락 하에서)
        groupBuyService.releaseSlots(participation.getGroupBuy().getId(), participation.getQuantity());

        return ParticipationResponse.from(participation);
    }

    /** PT-03. 실시간 참여 인원 조회 (Redis 우선, 없으면 DB 동기화) */
    @Transactional(readOnly = true)
    public ParticipationCountResponse getParticipationCount(Long groupBuyId) {
        GroupBuyService.ParticipationStockView stock = groupBuyService.getStockView(groupBuyId);
        return new ParticipationCountResponse(stock.currentCount(), stock.targetCount());
    }

    /** PT-04. 참여 이력 조회 */
    @Transactional(readOnly = true)
    public Page<ParticipationResponse> getMyParticipations(Long userId, ParticipationStatus status, Pageable pageable) {
        Page<Participation> page = (status != null)
                ? participationRepository.findByUser_IdAndStatus(userId, status, pageable)
                : participationRepository.findByUser_Id(userId, pageable);

        return page.map(ParticipationResponse::from);
    }

    @Transactional(readOnly = true)
    public ParticipationPaymentInfo getPaymentInfo(Long participationId) {
        Participation participation = participationRepository.findPaymentInfoById(participationId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPATION_NOT_FOUND));
        return new ParticipationPaymentInfo(
                participation.getId(),
                participation.getUser().getId(),
                participation.getGroupBuy().getId(),
                participation.getQuantity(),
                participation.getStatus() == ParticipationStatus.PARTICIPATING
        );
    }

    @Transactional
    public void confirmPayment(Long participationId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPATION_NOT_FOUND));
        if (participation.getStatus() == ParticipationStatus.CONFIRMED) {
            return;
        }
        if (participation.getStatus() != ParticipationStatus.PARTICIPATING) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        participation.confirm();
    }

    @Transactional
    public void refundPayment(Long participationId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPATION_NOT_FOUND));
        if (participation.getStatus() == ParticipationStatus.REFUNDED) {
            return;
        }
        if (participation.getStatus() != ParticipationStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        GroupBuy groupBuy = participation.getGroupBuy();
        if (groupBuy.getStatus() == GroupBuyStatus.RECRUITING
                && !groupBuy.isDeadlinePassed(LocalDateTime.now())) {
            groupBuyService.releaseSlots(groupBuy.getId(), participation.getQuantity());
        }
        participation.refund();
    }
}
