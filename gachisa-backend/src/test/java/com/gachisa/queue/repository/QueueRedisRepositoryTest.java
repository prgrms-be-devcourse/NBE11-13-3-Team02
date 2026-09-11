package com.gachisa.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.gachisa.queue.dto.ExpiredAdmission;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class QueueRedisRepositoryTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private QueueRedisRepository queueRepository;
    private Long groupBuyId;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(isRedisRunning(), "로컬 Redis가 실행 중일 때만 검증합니다.");

        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        queueRepository = new QueueRedisRepository(redisTemplate);
        groupBuyId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    @AfterEach
    void tearDown() {
        if (queueRepository != null && groupBuyId != null) {
            queueRepository.deleteQueue(groupBuyId);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void usersAreAdmittedInArrivalOrderAndQueueHasTtl() {
        queueRepository.enqueue(groupBuyId, 101L, "token-101");
        queueRepository.enqueue(groupBuyId, 102L, "token-102");

        assertThat(queueRepository.getWaitingPosition(groupBuyId, 101L)).isEqualTo(1L);
        assertThat(queueRepository.getWaitingPosition(groupBuyId, 102L)).isEqualTo(2L);

        queueRepository.admit(groupBuyId, 1, 10, Instant.now().plusSeconds(60));

        assertThat(queueRepository.getAdmissionExpiresAt(groupBuyId, 101L)).isNotNull();
        assertThat(queueRepository.getWaitingPosition(groupBuyId, 102L)).isEqualTo(1L);
        assertThat(redisTemplate.getExpire("queue:waiting:" + groupBuyId))
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofHours(24).getSeconds());
    }

    @Test
    void expiredAdmissionReturnsToEndOfQueueWithPaymentAttempt() {
        Instant expiresAt = Instant.now().plusSeconds(1);
        queueRepository.enqueue(groupBuyId, 101L, "token-101");
        queueRepository.enqueue(groupBuyId, 102L, "token-102");
        queueRepository.admit(groupBuyId, 1, 1, expiresAt);
        queueRepository.bindPaymentAttempt(groupBuyId, 101L, 9001L);

        List<ExpiredAdmission> expired =
                queueRepository.requeueExpired(groupBuyId, expiresAt.plusSeconds(1));

        assertThat(expired).containsExactly(new ExpiredAdmission(101L, 9001L));
        assertThat(queueRepository.getWaitingPosition(groupBuyId, 102L)).isEqualTo(1L);
        assertThat(queueRepository.getWaitingPosition(groupBuyId, 101L)).isEqualTo(2L);
    }

    private boolean isRedisRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 300);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
