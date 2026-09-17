package com.gachisa.groupbuy.startup;

import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 시 Redis 의 공동구매 재고를 DB 기준으로 다시 쓴다.
 *
 * <p>정원 예약은 Redis 가 먼저 판단하고(GroupBuyStockRedisRepository) DB 가 뒤따른다.
 * 두 저장소의 수명이 다른 게 문제다. 로컬·도커 환경은 {@code ddl-auto: create-drop} 이라
 * 기동할 때마다 MySQL 이 통째로 초기화되고 시드가 다시 들어가는데, Redis 는 볼륨에 남아
 * 이전 세대의 카운터를 그대로 들고 있다.
 *
 * <p>그러면 화면과 동작이 어긋난다. 실제로 이런 상태가 나왔다.
 *
 * <pre>
 *   MySQL  group_buy 2  current_count = 5 / target 6   → 목록은 "5/6 모집중"
 *   Redis  stock:current:2 = 6 / target 6              → 참여하면 409 GROUP_BUY_FULL
 * </pre>
 *
 * <p>DB 가 진실의 원본이므로 기동 시 한 번 덮어써 맞춘다. initIfAbsent 가 아니라
 * overwrite 를 쓴다 — 키가 이미 있는 게 바로 문제 상황이라, 있을 때 건너뛰면 고쳐지지 않는다.
 *
 * <p>모집중인 것만 맞춘다. 나머지 상태는 예약 자체가 막혀 있어 재고 키를 볼 일이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyStockSynchronizer {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyStockRedisRepository stockRedisRepository;

    /**
     * ApplicationReadyEvent 를 쓴다. 시드 SQL(data.sql)은 EntityManagerFactory 초기화
     * 단계에서 실행되므로, 그보다 늦은 이 시점이라야 시드된 값을 읽을 수 있다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncStock() {
        List<GroupBuy> recruiting = groupBuyRepository.findAllByStatus(GroupBuyStatus.RECRUITING);
        for (GroupBuy groupBuy : recruiting) {
            stockRedisRepository.overwrite(
                    groupBuy.getId(),
                    groupBuy.getCurrentCount(),
                    groupBuy.getTargetCount()
            );
        }
        log.info("공동구매 재고를 DB 기준으로 동기화했습니다: 모집중 {}건", recruiting.size());
    }
}
