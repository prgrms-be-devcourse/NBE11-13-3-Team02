package com.gachisa.groupbuy.startup;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository;
import com.gachisa.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 기동 시 Redis 재고 동기화 테스트.
 *
 * <p>이 로직은 기동할 때 한 번, 눈에 보이지 않게 돈다. 누가 나중에 overwrite 를
 * initIfAbsent 로 바꾸거나 이벤트 리스너를 떼어내도 애플리케이션은 멀쩡히 뜨고,
 * 문제는 한참 뒤에 "참여 버튼이 보이는데 눌러도 409" 로 나타난다. 실제로 그 증상을
 * 추적하는 데 오래 걸렸다. 여기서 막는다.
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyStockSynchronizerTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyStockRedisRepository stockRedisRepository;

    @Mock
    private Product product;

    private GroupBuyStockSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = new GroupBuyStockSynchronizer(groupBuyRepository, stockRedisRepository);
    }

    private GroupBuy recruiting(long id, int currentCount, int targetCount) {
        GroupBuy groupBuy = GroupBuy.builder()
                .product(product)
                .targetCount(targetCount)
                .discountRate(new BigDecimal("0.10"))
                .openAt(LocalDateTime.now().minusHours(1))
                .deadline(LocalDateTime.now().plusDays(1))
                .sellerId(9L)
                .build();
        ReflectionTestUtils.setField(groupBuy, "id", id);
        ReflectionTestUtils.setField(groupBuy, "currentCount", currentCount);
        return groupBuy;
    }

    @Test
    @DisplayName("모집중인 공동구매의 Redis 재고를 DB 값으로 덮어쓴다")
    void overwritesStockFromDatabase() {
        given(groupBuyRepository.findAllByStatus(GroupBuyStatus.RECRUITING))
                .willReturn(List.of(recruiting(1L, 3, 10), recruiting(2L, 5, 6)));

        synchronizer.syncStock();

        verify(stockRedisRepository).overwrite(1L, 3, 10);
        verify(stockRedisRepository).overwrite(2L, 5, 6);
    }

    @Test
    @DisplayName("initIfAbsent 가 아니라 overwrite 를 쓴다")
    void doesNotSkipWhenKeyAlreadyExists() {
        // 키가 이미 있는 상태가 바로 고치려는 문제다(MySQL 은 초기화됐는데 Redis 는 남은 경우).
        // initIfAbsent 는 키가 있으면 건너뛰므로 영영 맞춰지지 않는다.
        given(groupBuyRepository.findAllByStatus(GroupBuyStatus.RECRUITING))
                .willReturn(List.of(recruiting(2L, 5, 6)));

        synchronizer.syncStock();

        verify(stockRedisRepository).overwrite(2L, 5, 6);
        verify(stockRedisRepository, never()).initIfAbsent(2L, 5, 6);
    }

    @Test
    @DisplayName("모집중이 아닌 공동구매는 건드리지 않는다")
    void onlySynchronizesRecruiting() {
        given(groupBuyRepository.findAllByStatus(GroupBuyStatus.RECRUITING))
                .willReturn(List.of());

        synchronizer.syncStock();

        verify(groupBuyRepository).findAllByStatus(GroupBuyStatus.RECRUITING);
        verify(stockRedisRepository, never()).overwrite(anyLong(), anyInt(), anyInt());
    }
}
