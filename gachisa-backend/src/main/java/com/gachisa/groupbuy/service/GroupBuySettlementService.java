package com.gachisa.groupbuy.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import com.gachisa.participation.entity.Participation;
import com.gachisa.participation.entity.ParticipationStatus;
import com.gachisa.participation.repository.ParticipationRepository;
import com.gachisa.payment.dto.GroupBuyResultCommand;
import com.gachisa.payment.service.GroupBuyResultProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마감 배치의 실제 정산 처리를 담당한다.
 *
 * 예외적으로 이 클래스만 groupbuy/participation 두 도메인의 Repository를 함께 사용한다.
 * "group_buy 상태 변경 + 그에 속한 모든 participation 상태 변경"이 하나의 트랜잭션(원자적 처리)
 * 이어야 하는 정산이라는 특수한 책임 때문이며, 팀 컨벤션(도메인 간 Repository 직접 참조 금지)의
 * 명시적 예외 지점이다.
 *
 * settlement_batch 같은 별도 이력 테이블은 두지 않기로 했으므로(팀 결정),
 * 실패 시 로그로만 남기고 다음 스케줄 주기에 동일 대상이 재조회되어 재시도된다
 * (GroupBuy.settle()이 이미 처리된 건을 건드리지 않아 멱등성 보장).
 */
@Service
@RequiredArgsConstructor
public class GroupBuySettlementService {

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final GroupBuyResultProcessingService groupBuyResultProcessingService;

    @Transactional
    public void settleOne(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdForUpdate(groupBuyId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));

        if (groupBuy.getStatus() != GroupBuyStatus.RECRUITING) {
            return; // 이미 처리된 건 (스케줄 재실행에 대한 멱등성)
        }

        groupBuy.settle(); // RECRUITING -> ACHIEVED / NOT_ACHIEVED

        List<Long> paidParticipationIds = participationRepository
                .findByGroupBuy_IdAndStatus(groupBuyId, ParticipationStatus.CONFIRMED)
                .stream()
                .map(Participation::getId)
                .toList();

        if (!paidParticipationIds.isEmpty()) {
            GroupBuyResultCommand.Result result = groupBuy.getStatus() == GroupBuyStatus.ACHIEVED
                    ? GroupBuyResultCommand.Result.ACHIEVED
                    : GroupBuyResultCommand.Result.FAILED;
            groupBuyResultProcessingService.process(
                    new GroupBuyResultCommand(groupBuyId, result, paidParticipationIds)
            );
        }

        groupBuy.markSettled();
    }
}
