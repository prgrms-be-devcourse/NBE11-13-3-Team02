package com.gachisa.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository.ReserveResult;
import com.gachisa.product.entity.Product;
import com.gachisa.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupBuyServiceReserveSlotsTest {

    @Mock GroupBuyRepository groupBuyRepository;
    @Mock ProductRepository productRepository;
    @Mock GroupBuyStockRedisRepository stockRedisRepository;
    @Mock Product product;

    private GroupBuyService groupBuyService;

    @BeforeEach
    void setUp() {
        groupBuyService = new GroupBuyService(groupBuyRepository, productRepository, stockRedisRepository);
    }

    @Test
    @DisplayName("Redis 정원 초과면 DB 락을 걸지 않고 GROUP_BUY_FULL")
    void redisFullRejectsWithoutDbLock() {
        given(stockRedisRepository.tryReserve(1L, 1)).willReturn(ReserveResult.full());

        assertThatThrownBy(() -> groupBuyService.reserveSlots(1L, 1))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_FULL);

        verify(groupBuyRepository, never()).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("Redis 예약 성공 후 DB reserve 실패 시 Redis를 롤백한다")
    void rollsBackRedisWhenDbReserveFails() {
        GroupBuy closed = GroupBuy.builder()
                .product(product)
                .targetCount(10)
                .discountRate(new BigDecimal("0.10"))
                .openAt(LocalDateTime.now().minusHours(2))
                .deadline(LocalDateTime.now().minusHours(1))
                .sellerId(9L)
                .build();
        ReflectionTestUtils.setField(closed, "id", 1L);
        closed.cancelBySeller();

        given(stockRedisRepository.tryReserve(1L, 1)).willReturn(ReserveResult.ok(1));
        given(groupBuyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(closed));

        assertThatThrownBy(() -> groupBuyService.reserveSlots(1L, 1))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_CLOSED);

        verify(stockRedisRepository).release(1L, 1);
    }

    @Test
    @DisplayName("Redis 미초기화면 DB 값으로 init 후 재시도한다")
    void initializesRedisThenReserves() {
        GroupBuy groupBuy = GroupBuy.builder()
                .product(product)
                .targetCount(5)
                .discountRate(new BigDecimal("0.10"))
                .openAt(LocalDateTime.now().minusHours(1))
                .deadline(LocalDateTime.now().plusDays(1))
                .sellerId(9L)
                .build();
        ReflectionTestUtils.setField(groupBuy, "id", 1L);

        given(stockRedisRepository.tryReserve(1L, 1))
                .willReturn(ReserveResult.notInitialized())
                .willReturn(ReserveResult.ok(1));
        given(groupBuyRepository.findById(1L)).willReturn(Optional.of(groupBuy));
        given(groupBuyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(groupBuy));

        GroupBuy result = groupBuyService.reserveSlots(1L, 1);

        assertThat(result.getCurrentCount()).isEqualTo(1);
        verify(stockRedisRepository).initIfAbsent(1L, 0, 5);
    }
}
