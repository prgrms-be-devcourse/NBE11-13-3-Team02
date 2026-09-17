package com.gachisa.queue.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.groupbuy.dto.GroupBuyQueueInfo
import com.gachisa.groupbuy.entity.GroupBuyStatus
import com.gachisa.groupbuy.service.GroupBuyService
import com.gachisa.queue.repository.QueueRedisRepository
import com.gachisa.queue.metric.PaymentQueueMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class QueueServiceTest {
    @Mock private lateinit var queueRepository: QueueRedisRepository
    @Mock private lateinit var groupBuyService: GroupBuyService
    @Mock private lateinit var timeProvider: TimeProvider
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher
    @Mock private lateinit var paymentQueueMetrics: PaymentQueueMetrics
    private lateinit var queueService: QueueService
    @BeforeEach fun setUp() { queueService = QueueService(queueRepository, groupBuyService, timeProvider, eventPublisher, paymentQueueMetrics) }
    @Test fun closedGroupBuyQueueIsDeleted() {
        given(queueRepository.getGroupBuyIds()).willReturn(setOf("1"))
        given(groupBuyService.getQueueInfo(1L)).willReturn(GroupBuyQueueInfo(1L, 10, 1, NOW.minusDays(2), NOW.minusDays(1), GroupBuyStatus.SETTLED))
        given(timeProvider.now()).willReturn(NOW)
        queueService.processAllQueues()
        verify(queueRepository).deleteQueue(1L)
    }
    companion object { private val NOW = LocalDateTime.of(2026, 8, 24, 12, 0) }
}
