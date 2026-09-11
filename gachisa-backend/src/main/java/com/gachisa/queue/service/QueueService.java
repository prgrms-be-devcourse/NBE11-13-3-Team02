package com.gachisa.queue.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.groupbuy.dto.GroupBuyQueueInfo;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.queue.dto.QueueState;
import com.gachisa.queue.dto.QueueStatusResponse;
import com.gachisa.queue.dto.QueueTokenResponse;
import com.gachisa.queue.dto.ExpiredAdmission;
import com.gachisa.queue.event.QueueAdmissionExpiredEvent;
import com.gachisa.queue.repository.QueueRedisRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final Duration ADMISSION_TIMEOUT = Duration.ofMinutes(10);
    private static final int ADMISSION_BATCH_SIZE = 10;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final QueueRedisRepository queueRepository;
    private final GroupBuyService groupBuyService;
    private final TimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;

    public QueueTokenResponse issueToken(Long groupBuyId, Long userId) {
        GroupBuyQueueInfo groupBuy = getOpenGroupBuy(groupBuyId);
        String newToken = UUID.randomUUID().toString();
        queueRepository.enqueue(groupBuyId, userId, newToken);
        admitAvailable(groupBuy);

        String storedToken = queueRepository.getToken(groupBuyId, userId);
        QueueStatusResponse status = getStatusInternal(groupBuyId, userId);
        return new QueueTokenResponse(storedToken, status.status(), status.position(), status.admissionExpiresAt());
    }

    public QueueStatusResponse getStatus(Long groupBuyId, Long userId, String queueToken) {
        validateToken(groupBuyId, userId, queueToken);
        GroupBuyQueueInfo groupBuy = getOpenGroupBuy(groupBuyId);
        processExpired(groupBuy);
        admitAvailable(groupBuy);
        return getStatusInternal(groupBuyId, userId);
    }

    public void requireAdmission(Long groupBuyId, Long userId, String queueToken) {
        validateToken(groupBuyId, userId, queueToken);
        Instant expiresAt = queueRepository.getAdmissionExpiresAt(groupBuyId, userId);
        if (expiresAt == null) {
            throw new CustomException(ErrorCode.QUEUE_ADMISSION_REQUIRED);
        }
        if (!expiresAt.isAfter(nowInstant())) {
            throw new CustomException(ErrorCode.QUEUE_ADMISSION_EXPIRED);
        }
    }

    public void bindPaymentAttempt(Long groupBuyId, Long userId, Long paymentAttemptId) {
        queueRepository.bindPaymentAttempt(groupBuyId, userId, paymentAttemptId);
    }

    public void startConfirmation(Long groupBuyId, Long userId) {
        if (!queueRepository.startConfirmation(groupBuyId, userId, nowInstant())) {
            throw new CustomException(ErrorCode.QUEUE_ADMISSION_EXPIRED);
        }
    }

    public void confirmationFailed(Long groupBuyId, Long userId) {
        queueRepository.requeueConfirmation(groupBuyId, userId);
    }

    public void completeAdmission(Long groupBuyId, Long userId) {
        queueRepository.complete(groupBuyId, userId);
    }

    public void processAllQueues() {
        for (String groupBuyId : queueRepository.getGroupBuyIds()) {
            Long id = Long.valueOf(groupBuyId);
            GroupBuyQueueInfo groupBuy = groupBuyService.getQueueInfo(id);
            if (groupBuy.isOpen(timeProvider.now())) {
                processExpired(groupBuy);
                admitAvailable(groupBuy);
            } else {
                queueRepository.deleteQueue(id);
            }
        }
    }

    private void processExpired(GroupBuyQueueInfo groupBuy) {
        for (ExpiredAdmission expired : queueRepository.requeueExpired(
                groupBuy.groupBuyId(), nowInstant())) {
            if (expired.paymentAttemptId() != null) {
                eventPublisher.publishEvent(
                        new QueueAdmissionExpiredEvent(expired.paymentAttemptId()));
            }
        }
    }

    private void admitAvailable(GroupBuyQueueInfo groupBuy) {
        queueRepository.admit(
                groupBuy.groupBuyId(),
                groupBuy.remainingCount(),
                ADMISSION_BATCH_SIZE,
                nowInstant().plus(ADMISSION_TIMEOUT)
        );
    }

    private QueueStatusResponse getStatusInternal(Long groupBuyId, Long userId) {
        Instant expiresAt = queueRepository.getAdmissionExpiresAt(groupBuyId, userId);
        if (expiresAt != null) {
            return new QueueStatusResponse(QueueState.ADMITTED, null, toLocalDateTime(expiresAt));
        }
        if (queueRepository.isConfirming(groupBuyId, userId)) {
            return new QueueStatusResponse(QueueState.CONFIRMING, null, null);
        }
        Long position = queueRepository.getWaitingPosition(groupBuyId, userId);
        if (position == null) {
            throw new CustomException(ErrorCode.QUEUE_TOKEN_INVALID);
        }
        return new QueueStatusResponse(QueueState.WAITING, position, null);
    }

    private GroupBuyQueueInfo getOpenGroupBuy(Long groupBuyId) {
        GroupBuyQueueInfo groupBuy = groupBuyService.getQueueInfo(groupBuyId);
        if (!groupBuy.isOpen(timeProvider.now()) || groupBuy.remainingCount() == 0) {
            throw new CustomException(ErrorCode.QUEUE_NOT_OPEN);
        }
        return groupBuy;
    }

    private void validateToken(Long groupBuyId, Long userId, String queueToken) {
        String storedToken = queueRepository.getToken(groupBuyId, userId);
        if (queueToken == null || !queueToken.equals(storedToken)) {
            throw new CustomException(ErrorCode.QUEUE_TOKEN_INVALID);
        }
    }

    private Instant nowInstant() {
        return timeProvider.now().atZone(SEOUL).toInstant();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, SEOUL);
    }
}
