package com.gachisa.groupbuy.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * 공동구매 정원(currentCount)을 Redis에서 원자적으로 예약/해제한다.
 * DB 비관적 락 앞단에서 빠른 정원 체크·감소 DB 경합을 줄이는 게이트키퍼 역할.
 */
@Repository
@RequiredArgsConstructor
public class GroupBuyStockRedisRepository {

    private static final DefaultRedisScript<Long> INIT_SCRIPT =
            loadScript("redis/groupbuy-stock-init.lua", Long.class);
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            loadScript("redis/groupbuy-stock-reserve.lua", Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            loadScript("redis/groupbuy-stock-release.lua", Long.class);

    private final StringRedisTemplate redisTemplate;

    private static <T> DefaultRedisScript<T> loadScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

    /** target 키가 없을 때만 current/target을 세팅한다. */
    public void initIfAbsent(Long groupBuyId, int currentCount, int targetCount) {
        redisTemplate.execute(
                INIT_SCRIPT,
                List.of(currentKey(groupBuyId), targetKey(groupBuyId)),
                Integer.toString(currentCount),
                Integer.toString(targetCount)
        );
    }

    /** 생성/강제 동기화 시 Redis 값을 DB 기준으로 덮어쓴다. */
    public void overwrite(Long groupBuyId, int currentCount, int targetCount) {
        redisTemplate.opsForValue().set(currentKey(groupBuyId), Integer.toString(currentCount));
        redisTemplate.opsForValue().set(targetKey(groupBuyId), Integer.toString(targetCount));
    }

    /**
     * @return 예약 성공 시 예약 후 currentCount,
     *         정원 초과 시 empty,
     *         미초기화 시 empty (호출측에서 init 후 재시도)
     */
    public ReserveResult tryReserve(Long groupBuyId, int quantity) {
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(currentKey(groupBuyId), targetKey(groupBuyId)),
                Integer.toString(quantity)
        );
        if (result == null || result == -1L) {
            return ReserveResult.notInitialized();
        }
        if (result == 0L) {
            return ReserveResult.full();
        }
        return ReserveResult.ok(result.intValue());
    }

    public int release(Long groupBuyId, int quantity) {
        Long result = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(currentKey(groupBuyId)),
                Integer.toString(quantity)
        );
        return result == null ? 0 : result.intValue();
    }

    public Optional<Integer> getCurrentCount(Long groupBuyId) {
        String value = redisTemplate.opsForValue().get(currentKey(groupBuyId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value));
    }

    public Optional<Integer> getTargetCount(Long groupBuyId) {
        String value = redisTemplate.opsForValue().get(targetKey(groupBuyId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value));
    }

    private String currentKey(Long groupBuyId) {
        return "groupbuy:stock:current:" + groupBuyId;
    }

    private String targetKey(Long groupBuyId) {
        return "groupbuy:stock:target:" + groupBuyId;
    }

    public record ReserveResult(Status status, Integer currentCountAfter) {
        public enum Status { OK, FULL, NOT_INITIALIZED }

        public static ReserveResult ok(int currentCountAfter) {
            return new ReserveResult(Status.OK, currentCountAfter);
        }

        public static ReserveResult full() {
            return new ReserveResult(Status.FULL, null);
        }

        public static ReserveResult notInitialized() {
            return new ReserveResult(Status.NOT_INITIALIZED, null);
        }

        public boolean isOk() {
            return status == Status.OK;
        }

        public boolean isFull() {
            return status == Status.FULL;
        }

        public boolean isNotInitialized() {
            return status == Status.NOT_INITIALIZED;
        }
    }
}
