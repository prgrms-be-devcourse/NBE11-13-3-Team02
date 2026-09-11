package com.gachisa.queue.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.groupbuy.dto.GroupBuyQueueInfo;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.queue.repository.QueueRedisRepository;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Mock QueueRedisRepository queueRepository;
    @Mock GroupBuyService groupBuyService;
    @Mock TimeProvider timeProvider;
    @Mock ApplicationEventPublisher eventPublisher;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                queueRepository, groupBuyService, timeProvider, eventPublisher);
    }

    @Test
    void closedGroupBuyQueueIsDeleted() {
        given(queueRepository.getGroupBuyIds()).willReturn(Set.of("1"));
        given(groupBuyService.getQueueInfo(1L)).willReturn(
                new GroupBuyQueueInfo(
                        1L, 10, 1, NOW.minusDays(2), NOW.minusDays(1),
                        GroupBuyStatus.SETTLED)
        );
        given(timeProvider.now()).willReturn(NOW);

        queueService.processAllQueues();

        verify(queueRepository).deleteQueue(1L);
    }
}
