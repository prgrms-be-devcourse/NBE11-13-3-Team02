package com.gachisa.groupbuy.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.groupbuy.dto.GroupBuyCreateRequest;
import com.gachisa.groupbuy.dto.GroupBuyDetailResponse;
import com.gachisa.groupbuy.dto.GroupBuyPaymentInfo;
import com.gachisa.groupbuy.dto.GroupBuyQueueInfo;
import com.gachisa.groupbuy.dto.GroupBuyResponse;
import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import com.gachisa.groupbuy.repository.GroupBuyRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository;
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository.ReserveResult;
import com.gachisa.product.entity.Product;
import com.gachisa.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private final GroupBuyRepository groupBuyRepository;
    private final ProductRepository productRepository;
    private final GroupBuyStockRedisRepository stockRedisRepository;

    /** GB-01 */
    @Transactional
    public GroupBuyResponse createGroupBuy(Long sellerId, GroupBuyCreateRequest request) {
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        GroupBuy groupBuy = GroupBuy.builder()
            .product(product)
            .targetCount(request.getTargetCount())
            .discountRate(request.getDiscountRate())
            .openAt(request.getOpenAt())
            .deadline(request.getDeadline())
            .sellerId(sellerId)
            .build();

        groupBuyRepository.save(groupBuy);
        stockRedisRepository.overwrite(groupBuy.getId(), groupBuy.getCurrentCount(), groupBuy.getTargetCount());
        return GroupBuyResponse.from(groupBuy);
    }

    /** GB-02 (필터 없는 기본 목록) - 파라미터 2개 */
    @Transactional(readOnly = true)
    public Page<GroupBuyResponse> getGroupBuyList(GroupBuyStatus status, Pageable pageable) {
        GroupBuyStatus target = (status != null) ? status : GroupBuyStatus.RECRUITING;
        return groupBuyRepository.findByStatus(target, pageable)
            .map(GroupBuyResponse::from);
    }

    /**
     * 검색 전용 (GET /api/group-buys/search) - 파라미터 7개
     * 가격 필터/정렬은 할인가(basePrice - basePrice*discountRate) 기준으로 DB에서 직접 계산한다.
     * sort 값: "price_asc" / "price_desc" / 그 외(기본값) = 마감임박순
     * (인기순은 참여 인원만으로는 지표가 부족하다고 판단해 제외함)
     */
    @Transactional(readOnly = true)
    public Page<GroupBuyResponse> searchGroupBuy(GroupBuyStatus status, String keyword, Long categoryId,
                                                 Integer minPrice, Integer maxPrice, String sort,
                                                 Pageable pageable) {
        GroupBuyStatus targetStatus = (status != null) ? status : GroupBuyStatus.RECRUITING;
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword : null;
        String sortKey = (sort != null) ? sort.trim().toLowerCase() : "deadline";

        Page<GroupBuy> page = switch (sortKey) {
            case "price_asc" -> groupBuyRepository.searchOrderByPriceAsc(
                targetStatus, normalizedKeyword, categoryId, minPrice, maxPrice, pageable);
            case "price_desc" -> groupBuyRepository.searchOrderByPriceDesc(
                targetStatus, normalizedKeyword, categoryId, minPrice, maxPrice, pageable);
            default -> groupBuyRepository.searchOrderByDeadline(
                targetStatus, normalizedKeyword, categoryId, minPrice, maxPrice, pageable);
        };

        return page.map(GroupBuyResponse::from);
    }

    /** GB-03 */
    @Transactional(readOnly = true)
    public GroupBuyDetailResponse getGroupBuyDetail(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));

        long remainingSeconds = Math.max(0,
            Duration.between(LocalDateTime.now(), groupBuy.getDeadline()).getSeconds());

        return GroupBuyDetailResponse.of(groupBuy, remainingSeconds);
    }

    /** GB-05: 판매자가 마감 전 취소. 관리자는 소유 여부와 무관하게 취소 가능(운영 목적). */
    @Transactional
    public GroupBuyResponse cancelGroupBuy(Long userId, Long groupBuyId, boolean isAdmin) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));

        if (!isAdmin && !groupBuy.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        groupBuy.cancelBySeller();
        return GroupBuyResponse.from(groupBuy);
    }

    /**
     * 참여 도메인에서 호출하는 핵심 메서드.
     * 1) Redis Lua로 정원을 원자 예약 (빠른 게이트키퍼)
     * 2) DB SELECT FOR UPDATE 로 row 잠근 뒤 currentCount 반영 (최종 정합성)
     * Redis 예약 성공 후 DB가 실패하면 Redis를 즉시 롤백한다.
     */
    @Transactional
    public GroupBuy reserveSlots(Long groupBuyId, int quantity) {
        reserveInRedis(groupBuyId, quantity);
        try {
            GroupBuy groupBuy = groupBuyRepository.findByIdForUpdate(groupBuyId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
            groupBuy.reserve(quantity);
            return groupBuy;
        } catch (RuntimeException ex) {
            stockRedisRepository.release(groupBuyId, quantity);
            throw ex;
        }
    }

    /** 참여 취소 시 인원 롤백 (DB 락 + Redis) */
    @Transactional
    public void releaseSlots(Long groupBuyId, int quantity) {
        GroupBuy groupBuy = groupBuyRepository.findByIdForUpdate(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
        groupBuy.release(quantity);
        stockRedisRepository.release(groupBuyId, quantity);
    }

    /** 참여 도메인(PT-03 등)이 락 없이 가볍게 엔티티를 조회할 때 사용 */
    @Transactional(readOnly = true)
    public GroupBuy getGroupBuyEntityOrThrow(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
    }

    /**
     * 실시간 참여 인원: Redis 우선, 없으면 DB에서 읽어 Redis를 초기화한다.
     */
    @Transactional(readOnly = true)
    public ParticipationStockView getStockView(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyEntityOrThrow(groupBuyId);
        Integer current = stockRedisRepository.getCurrentCount(groupBuyId).orElse(null);
        Integer target = stockRedisRepository.getTargetCount(groupBuyId).orElse(null);
        if (current == null || target == null) {
            stockRedisRepository.initIfAbsent(
                groupBuyId, groupBuy.getCurrentCount(), groupBuy.getTargetCount());
            current = stockRedisRepository.getCurrentCount(groupBuyId).orElse(groupBuy.getCurrentCount());
            target = stockRedisRepository.getTargetCount(groupBuyId).orElse(groupBuy.getTargetCount());
        }
        return new ParticipationStockView(current, target);
    }

    @Transactional(readOnly = true)
    public GroupBuyPaymentInfo getPaymentInfo(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyEntityOrThrow(groupBuyId);
        return new GroupBuyPaymentInfo(
            groupBuy.getId(),
            groupBuy.getProduct().getId(),
            groupBuy.getDiscountRate()
        );
    }

    @Transactional(readOnly = true)
    public GroupBuyQueueInfo getQueueInfo(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyEntityOrThrow(groupBuyId);
        int current = stockRedisRepository.getCurrentCount(groupBuyId).orElse(groupBuy.getCurrentCount());
        return new GroupBuyQueueInfo(
            groupBuy.getId(),
            groupBuy.getTargetCount(),
            current,
            groupBuy.getOpenAt(),
            groupBuy.getDeadline(),
            groupBuy.getStatus()
        );
    }

    private void reserveInRedis(Long groupBuyId, int quantity) {
        ReserveResult first = stockRedisRepository.tryReserve(groupBuyId, quantity);
        if (first.isOk()) {
            return;
        }
        if (first.isFull()) {
            throw new CustomException(ErrorCode.GROUP_BUY_FULL);
        }

        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(ErrorCode.GROUP_BUY_NOT_FOUND));
        stockRedisRepository.initIfAbsent(groupBuyId, groupBuy.getCurrentCount(), groupBuy.getTargetCount());

        ReserveResult second = stockRedisRepository.tryReserve(groupBuyId, quantity);
        if (second.isFull() || second.isNotInitialized()) {
            throw new CustomException(ErrorCode.GROUP_BUY_FULL);
        }
    }

    public record ParticipationStockView(int currentCount, int targetCount) {
    }
}
