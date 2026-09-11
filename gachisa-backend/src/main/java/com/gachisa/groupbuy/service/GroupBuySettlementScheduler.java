package com.gachisa.groupbuy.service;

import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GB-04 / PAY-02. 마감 시각이 지난 공동구매를 주기적으로 조회해 정산을 트리거한다.
 *
 * settleOne() 호출은 별도 빈(GroupBuySettlementService)의 @Transactional 메서드를
 * 외부에서 호출하는 형태라야 프록시가 트랜잭션을 정상적으로 적용한다.
 * (같은 클래스 안에서 this.xxx()로 호출하면 self-invocation 문제로 트랜잭션이 안 걸림)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuySettlementScheduler {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuySettlementService settlementService;

    @Scheduled(fixedDelay = 60_000) // 1분마다 실행 (개발 중엔 짧게, 운영에서는 조정)
    public void settleExpiredGroupBuys() {
        List<GroupBuy> targets =
                groupBuyRepository.findByStatusAndDeadlineBefore(GroupBuyStatus.RECRUITING, LocalDateTime.now());

        for (GroupBuy target : targets) {
            try {
                settlementService.settleOne(target.getId());
            } catch (Exception e) {
                // TODO(장애예방, 3차): 실패 건 재시도 큐 적재, Circuit Breaker 연동
                log.error("공동구매 정산 실패 - groupBuyId={}", target.getId(), e);
            }
        }
    }
}
