package com.gachisa.participation.entity;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "participation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipationStatus status;

    @Column(name = "participated_at", nullable = false)
    private LocalDateTime participatedAt;

    @Builder
    private Participation(GroupBuy groupBuy, User user, Integer quantity) {
        this.groupBuy = groupBuy;
        this.user = user;
        this.quantity = quantity;
        this.status = ParticipationStatus.PARTICIPATING;
        this.participatedAt = LocalDateTime.now();
    }

    /** 결제 완료 콜백에서 호출 (강한 동기화: payment 처리와 같은 트랜잭션 내에서 호출되어야 함) */
    public void confirm() {
        this.status = ParticipationStatus.CONFIRMED;
    }

    /** "참여중" 상태에서만 직접 취소 가능. 확정 이후는 cancel()이 아니라 refund()로만 전환된다. */
    public void cancel() {
        if (status != ParticipationStatus.PARTICIPATING) {
            throw new CustomException(ErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }
        this.status = ParticipationStatus.CANCELLED;
    }

    /** 확정 이후(배송 전 취소 or 배송 후 반품) 환불 처리 시 결제 담당 모듈에서 호출 */
    public void refund() {
        this.status = ParticipationStatus.REFUNDED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public boolean isCancelable() {
        return this.status == ParticipationStatus.PARTICIPATING;
    }
}
