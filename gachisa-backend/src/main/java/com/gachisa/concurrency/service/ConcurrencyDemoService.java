package com.gachisa.concurrency.service;

import com.gachisa.concurrency.dto.ConcurrencyStressRequest;
import com.gachisa.concurrency.dto.ConcurrencyStressRequest.Mode;
import com.gachisa.concurrency.dto.ConcurrencyStressResponse;
import com.gachisa.concurrency.dto.GroupBuyListItem;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository;
import com.gachisa.groupbuy.service.GroupBuyService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;
/**
 * 공동구매 정원 동시성 문제를 재현/검증하기 위한 로컬 스트레스 도구.
 * 실제 참여(Participation) 레코드는 만들지 않고 currentCount 예약만 경쟁시킨다.
 */
@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class ConcurrencyDemoService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyStockRedisRepository stockRedisRepository;
    private final GroupBuyService groupBuyService;
    private final PlatformTransactionManager transactionManager;

    /**
     * 데모 페이지에서 groupBuyId를 직접 타이핑하지 않고 드롭다운으로 고를 수 있도록
     * DB에 저장된 GroupBuy 목록을 내려준다. (local 전용, 개발 편의용이라 페이징 없이 전체 조회)
     */
    @Transactional(readOnly = true)
    public List<GroupBuyListItem> listGroupBuys() {
        return groupBuyRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
            .map(gb -> GroupBuyListItem.builder()
                .id(gb.getId())
                .productName(gb.getProduct().getName())
                .status(gb.getStatus())
                .currentCount(gb.getCurrentCount())
                .targetCount(gb.getTargetCount())
                .build())
            .toList();
    }

    public ConcurrencyStressResponse run(Long groupBuyId, ConcurrencyStressRequest request) {
        GroupBuy snapshot = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));

        int originalCount = snapshot.getCurrentCount();
        int target = snapshot.getTargetCount();
        // 이미 마감된 공동구매에 다시 때리면 전부 GROUP_BUY_FULL 이라 성공 0이 된다.
        // 데모는 currentCount를 0으로 되돌린 뒤 같은 정원을 경쟁시킨다.
        resetStock(groupBuyId, 0, target);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejectedAsFull = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(request.getThreadCount());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(request.getThreadCount());

        ExecutorService pool = Executors.newFixedThreadPool(request.getThreadCount());
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < request.getThreadCount(); i++) {
                futures.add(pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        attemptReserve(groupBuyId, request.getQuantityPerRequest(), request.getMode());
                        success.incrementAndGet();
                    } catch (Exception ex) {
                        CustomException custom = unwrapCustomException(ex);
                        if (custom != null && custom.getErrorCode() == ErrorCode.GROUP_BUY_FULL) {
                            rejectedAsFull.incrementAndGet();
                        } else {
                            otherFailures.incrementAndGet();
                        }
                    } finally {
                        done.countDown();
                    }
                }));
            }

            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            start.countDown();
            if (!done.await(60, TimeUnit.SECONDS)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            for (Future<?> future : futures) {
                future.get(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        } finally {
            pool.shutdownNow();
        }

        GroupBuy afterEntity = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
        int afterDb = afterEntity.getCurrentCount();
        Integer afterRedis = stockRedisRepository.getCurrentCount(groupBuyId).orElse(null);
        int failures = rejectedAsFull.get() + otherFailures.get();

        // --- 오버셀 판정 ---
        // 1) DB currentCount 자체가 target을 넘은 경우 (원자적 UPDATE 방식이었다면 이렇게 드러남)
        // 2) DB값은 target 이하인데 successCount가 target을 넘은 경우
        //    → UNSAFE 모드는 read-modify-write로 currentCount를 "리터럴 값으로 덮어쓰기" 하기 때문에
        //      여러 트랜잭션이 같은 스냅샷(예: 0)을 읽고 각자 "1"을 계산해 커밋하면
        //      최종 DB값은 낮게 나오면서(Lost Update) 성공 응답만 target을 초과해서 발생한다.
        //      이 케이스가 실무에서 더 위험하다: 응답은 성공인데 실제 반영이 안 된 상태이기 때문.
        boolean dbOversold = afterDb > target;
        boolean lostUpdate = !dbOversold && success.get() > target;
        boolean oversold = dbOversold || lostUpdate;

        String summary;
        if (dbOversold) {
            summary = String.format(
                "동시성 문제 재현: DB currentCount(%d) > targetCount(%d)", afterDb, target);
        } else if (lostUpdate) {
            summary = String.format(
                "동시성 문제 재현(Lost Update): 성공 응답 %d건이 정원 %d명을 초과했지만 "
                    + "최종 DB currentCount는 %d로 오히려 낮게 나타남 "
                    + "(동시에 읽은 스냅샷을 각자 덮어써서 일부 반영분이 유실됨)",
                success.get(), target, afterDb);
        } else {
            summary = String.format(
                "정원 준수: 성공 %d / 정원초과거절 %d / 기타실패 %d (target %d)",
                success.get(), rejectedAsFull.get(), otherFailures.get(), target);
        }

        return ConcurrencyStressResponse.builder()
            .mode(request.getMode())
            .groupBuyId(groupBuyId)
            .threadCount(request.getThreadCount())
            .quantityPerRequest(request.getQuantityPerRequest())
            .targetCount(target)
            .currentCountBefore(originalCount)
            .currentCountAfterDb(afterDb)
            .currentCountAfterRedis(afterRedis)
            .remainingSlotsAtStart(target)
            .successCount(success.get())
            .failureCount(failures)
            .rejectedAsFull(rejectedAsFull.get())
            .otherFailures(otherFailures.get())
            .oversold(oversold)
            .summary(summary)
            .build();
    }

    private void resetStock(Long groupBuyId, int currentCount, int targetCount) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            GroupBuy groupBuy = groupBuyRepository.findByIdForUpdate(groupBuyId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
            groupBuy.resetCurrentCount(currentCount);
        });
        try {
            stockRedisRepository.overwrite(groupBuyId, currentCount, targetCount);
        } catch (RuntimeException ex) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void attemptReserve(Long groupBuyId, int quantity, Mode mode) {
        switch (mode) {
            case UNSAFE -> unsafeReserve(groupBuyId, quantity);
            case DB_LOCK -> dbLockReserve(groupBuyId, quantity);
            case REDIS_AND_DB -> groupBuyService.reserveSlots(groupBuyId, quantity);
        }
    }

    /** 의도적으로 락 없이 읽고 증가 — Lost Update / 초과 모집 재현용 */
    private void unsafeReserve(Long groupBuyId, int quantity) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            groupBuy.reserve(quantity);
        });
    }

    private void dbLockReserve(Long groupBuyId, int quantity) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            GroupBuy groupBuy = groupBuyRepository.findByIdForUpdate(groupBuyId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
            groupBuy.reserve(quantity);
        });
    }

    private CustomException unwrapCustomException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof CustomException custom) {
                return custom;
            }
            current = current.getCause();
        }
        return null;
    }
}
