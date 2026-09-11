package com.gachisa.groupbuy.entity;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_buy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount;

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountRate;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupBuyStatus status;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Builder
    private GroupBuy(Product product, Integer targetCount,
                     BigDecimal discountRate, LocalDateTime openAt, LocalDateTime deadline,
                     Long sellerId) {
        if (deadline.isBefore(openAt)) {
            throw new CustomException(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        }
        this.product = product;
        this.targetCount = targetCount;
        this.currentCount = 0;
        this.discountRate = discountRate;
        this.openAt = openAt;
        this.deadline = deadline;
        this.sellerId = sellerId;
        this.status = GroupBuyStatus.RECRUITING;
    }

    /**
     * 참여 신청 시 인원을 예약한다.
     * 반드시 비관적 락으로 조회된 인스턴스에서 호출되어야 동시성이 보장된다.
     * (GroupBuyRepository.findByIdForUpdate 참고)
     */
    public void reserve(int quantity) {
        if (status != GroupBuyStatus.RECRUITING) {
            throw new CustomException(ErrorCode.GROUP_BUY_CLOSED);
        }
        if (currentCount + quantity > targetCount) {
            throw new CustomException(ErrorCode.GROUP_BUY_FULL);
        }
        this.currentCount += quantity;
    }

    /** 참여 취소 시 예약 인원을 롤백한다. 락 하에서 호출되어야 한다. */
    public void release(int quantity) {
        this.currentCount = Math.max(0, this.currentCount - quantity);
    }

    /** 동시성 데모에서 정원을 비우고 다시 경쟁시키기 위해 사용 */
    public void resetCurrentCount(int value) {
        this.currentCount = Math.max(0, value);
    }

    public void cancelBySeller() {
        if (status != GroupBuyStatus.RECRUITING) {
            throw new CustomException(ErrorCode.GROUP_BUY_CANNOT_CANCEL);
        }
        this.status = GroupBuyStatus.CANCELLED;
    }

    /** 마감 배치에서 목표 달성 여부에 따라 호출 */
    public void settle() {
        if (status != GroupBuyStatus.RECRUITING) {
            return; // 이미 처리됨 (배치 재실행에 대한 멱등성 보장)
        }
        this.status = (currentCount >= targetCount) ? GroupBuyStatus.ACHIEVED : GroupBuyStatus.NOT_ACHIEVED;
    }

    public void markSettled() {
        this.status = GroupBuyStatus.SETTLED;
    }

    public boolean isOwnedBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public boolean isDeadlinePassed(LocalDateTime now) {
        return !now.isBefore(this.deadline);
    }

    public double getProgressRate() {
        if (targetCount == 0) return 0.0;
        return Math.round((currentCount * 1000.0 / targetCount)) / 10.0; // 소수 첫째자리
    }
}
