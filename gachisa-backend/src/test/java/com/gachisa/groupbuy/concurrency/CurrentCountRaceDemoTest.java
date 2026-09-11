package com.gachisa.groupbuy.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DB currentCount 동시성 문제를 "코드로 확인"하기 위한 순수 동시성 데모 테스트.
 * 외부 MySQL/Redis 없이 Lost Update / 초과 모집 패턴을 재현한다.
 */
class CurrentCountRaceDemoTest {

    @Test
    @DisplayName("락 없는 read-check-write는 target을 초과할 수 있다")
    void unsafeReserveCanOversell() throws Exception {
        final int target = 10;
        final int threads = 50;
        UnsafeCounter counter = new UnsafeCounter(0, target);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    counter.tryReserve(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdownNow();

        assertThat(counter.getCurrent())
                .as("락이 없으면 동시에 통과한 요청 때문에 target을 넘길 수 있다")
                .isGreaterThan(target);
    }

    @Test
    @DisplayName("원자적 예약(Redis Lua와 동일 패턴)은 target을 넘지 않는다")
    void atomicReserveNeverOversells() throws Exception {
        final int target = 10;
        final int threads = 50;
        AtomicStock stock = new AtomicStock(0, target);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    if (stock.tryReserve(1)) {
                        success.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdownNow();

        assertThat(stock.getCurrent()).isEqualTo(target);
        assertThat(success.get()).isEqualTo(target);
        assertThat(failure.get()).isEqualTo(threads - target);
    }

    /** JPA findById + 메모리 체크 + save 와 같은 TOCTOU(검사 시점과 갱신 시점 불일치) 패턴 */
    static final class UnsafeCounter {
        private int current;
        private final int target;

        UnsafeCounter(int current, int target) {
            this.current = current;
            this.target = target;
        }

        void tryReserve(int quantity) {
            int snapshot = current;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 스냅샷으로 검사한 뒤, 실제 쓰기는 최신 current에 더한다 → 초과 모집 가능
            if (snapshot + quantity <= target) {
                current += quantity;
            }
        }

        int getCurrent() {
            return current;
        }
    }

    /** Redis INCRBY + 한도 체크를 한 스크립트로 수행하는 것과 동일한 원자성 */
    static final class AtomicStock {
        private final AtomicInteger current;
        private final int target;

        AtomicStock(int current, int target) {
            this.current = new AtomicInteger(current);
            this.target = target;
        }

        boolean tryReserve(int quantity) {
            while (true) {
                int now = current.get();
                if (now + quantity > target) {
                    return false;
                }
                if (current.compareAndSet(now, now + quantity)) {
                    return true;
                }
            }
        }

        int getCurrent() {
            return current.get();
        }
    }
}
