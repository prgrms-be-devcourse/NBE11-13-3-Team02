package com.gachisa.queue.repository;

import com.gachisa.queue.dto.ExpiredAdmission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private static final String GROUPS_KEY = "queue:groups";
    private static final Duration QUEUE_IDLE_TTL = Duration.ofHours(24);

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT =
            loadScript("redis/queue-enqueue.lua", Long.class);
    private static final DefaultRedisScript<List> ADMIT_SCRIPT =
            loadScript("redis/queue-admit.lua", List.class);
    private static final DefaultRedisScript<List> REQUEUE_EXPIRED_SCRIPT =
            loadScript("redis/queue-requeue-expired.lua", List.class);
    private static final DefaultRedisScript<Long> START_CONFIRMATION_SCRIPT =
            loadScript("redis/queue-start-confirmation.lua", Long.class);
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT =
            loadScript("redis/queue-complete.lua", Long.class);
    private static final DefaultRedisScript<Long> REQUEUE_CONFIRMATION_SCRIPT =
            loadScript("redis/queue-requeue-confirmation.lua", Long.class);

    private final StringRedisTemplate redisTemplate;

    private static <T> DefaultRedisScript<T> loadScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

    public void enqueue(Long groupBuyId, Long userId, String queueToken) {
        redisTemplate.execute(
                ENQUEUE_SCRIPT,
                List.of(waitingKey(groupBuyId), sequenceKey(groupBuyId), tokenKey(groupBuyId), GROUPS_KEY),
                userId.toString(), queueToken, groupBuyId.toString()
        );
        refreshTtl(groupBuyId);
    }

    public void admit(Long groupBuyId, int capacity, int batchSize, Instant expiresAt) {
        redisTemplate.execute(
                ADMIT_SCRIPT,
                List.of(waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId)),
                Integer.toString(capacity), Integer.toString(batchSize), Long.toString(expiresAt.toEpochMilli())
        );
        refreshTtl(groupBuyId);
    }

    public List<ExpiredAdmission> requeueExpired(Long groupBuyId, Instant now) {
        List<?> values = redisTemplate.execute(
                REQUEUE_EXPIRED_SCRIPT,
                List.of(activeKey(groupBuyId), waitingKey(groupBuyId), sequenceKey(groupBuyId), attemptKey(groupBuyId)),
                Long.toString(now.toEpochMilli())
        );
        refreshTtl(groupBuyId);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExpiredAdmission> result = new ArrayList<>();
        for (int index = 0; index + 1 < values.size(); index += 2) {
            String attemptId = String.valueOf(values.get(index + 1));
            result.add(new ExpiredAdmission(
                    Long.valueOf(String.valueOf(values.get(index))),
                    attemptId.isBlank() ? null : Long.valueOf(attemptId)
            ));
        }
        return result;
    }

    public boolean startConfirmation(Long groupBuyId, Long userId, Instant now) {
        Long result = redisTemplate.execute(
                START_CONFIRMATION_SCRIPT,
                List.of(activeKey(groupBuyId), confirmingKey(groupBuyId)),
                userId.toString(), Long.toString(now.toEpochMilli())
        );
        refreshTtl(groupBuyId);
        return Long.valueOf(1L).equals(result);
    }

    public void complete(Long groupBuyId, Long userId) {
        redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(waitingKey(groupBuyId), activeKey(groupBuyId), confirmingKey(groupBuyId),
                        tokenKey(groupBuyId), attemptKey(groupBuyId)),
                userId.toString()
        );
        refreshTtl(groupBuyId);
    }

    public void requeueConfirmation(Long groupBuyId, Long userId) {
        redisTemplate.execute(
                REQUEUE_CONFIRMATION_SCRIPT,
                List.of(confirmingKey(groupBuyId), waitingKey(groupBuyId), sequenceKey(groupBuyId), attemptKey(groupBuyId)),
                userId.toString()
        );
        refreshTtl(groupBuyId);
    }

    public void bindPaymentAttempt(Long groupBuyId, Long userId, Long paymentAttemptId) {
        redisTemplate.opsForHash().put(attemptKey(groupBuyId), userId.toString(), paymentAttemptId.toString());
        refreshTtl(groupBuyId);
    }

    public String getToken(Long groupBuyId, Long userId) {
        Object value = redisTemplate.opsForHash().get(tokenKey(groupBuyId), userId.toString());
        return value == null ? null : value.toString();
    }

    public Long getWaitingPosition(Long groupBuyId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(groupBuyId), userId.toString());
        return rank == null ? null : rank + 1;
    }

    public Instant getAdmissionExpiresAt(Long groupBuyId, Long userId) {
        Double score = redisTemplate.opsForZSet().score(activeKey(groupBuyId), userId.toString());
        return score == null ? null : Instant.ofEpochMilli(score.longValue());
    }

    public boolean isConfirming(Long groupBuyId, Long userId) {
        return redisTemplate.opsForZSet().score(confirmingKey(groupBuyId), userId.toString()) != null;
    }

    public Set<String> getGroupBuyIds() {
        Set<String> values = redisTemplate.opsForSet().members(GROUPS_KEY);
        return values == null ? Collections.emptySet() : values;
    }

    public void deleteQueue(Long groupBuyId) {
        redisTemplate.delete(List.of(
                waitingKey(groupBuyId),
                activeKey(groupBuyId),
                confirmingKey(groupBuyId),
                tokenKey(groupBuyId),
                attemptKey(groupBuyId),
                sequenceKey(groupBuyId)
        ));
        redisTemplate.opsForSet().remove(GROUPS_KEY, groupBuyId.toString());
    }

    private void refreshTtl(Long groupBuyId) {
        for (String key : List.of(
                waitingKey(groupBuyId),
                activeKey(groupBuyId),
                confirmingKey(groupBuyId),
                tokenKey(groupBuyId),
                attemptKey(groupBuyId),
                sequenceKey(groupBuyId)
        )) {
            redisTemplate.expire(key, QUEUE_IDLE_TTL);
        }
    }

    private String waitingKey(Long groupBuyId) {
        return "queue:waiting:" + groupBuyId;
    }

    private String activeKey(Long groupBuyId) {
        return "queue:active:" + groupBuyId;
    }

    private String confirmingKey(Long groupBuyId) {
        return "queue:confirming:" + groupBuyId;
    }

    private String tokenKey(Long groupBuyId) {
        return "queue:tokens:" + groupBuyId;
    }

    private String attemptKey(Long groupBuyId) {
        return "queue:attempts:" + groupBuyId;
    }

    private String sequenceKey(Long groupBuyId) {
        return "queue:sequence:" + groupBuyId;
    }

}
