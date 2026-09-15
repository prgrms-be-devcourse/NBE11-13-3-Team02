package com.gachisa.queue.client;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 별도 프로세스로 분리된 대기열 서비스(gachisa-queue) 호출 클라이언트.
 *
 * <p>분리 전 인프로세스 QueueService와 같은 메서드 이름을 유지해 호출부가 바뀌지 않게 했다.
 * 호출부 입장에서 달라진 것은 "이제 네트워크를 탄다"는 사실뿐이다.
 *
 * <p>대기열 서비스는 실패를 HTTP 상태 + 본문의 ErrorCode 이름으로 알려준다. 여기서 다시
 * CustomException으로 바꿔 줘야 기존 예외 처리와 프론트엔드 분기가 그대로 동작한다.
 */
@Component
public class QueueClient {

    private static final String USER_PATH = "/internal/queues/{groupBuyId}/users/{userId}";

    private final RestClient restClient;

    public QueueClient(@Qualifier("queueRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void requireAdmission(Long groupBuyId, Long userId, String queueToken) {
        post(groupBuyId, userId, "/require-admission?queueToken={queueToken}", null, queueToken);
    }

    public void bindPaymentAttempt(Long groupBuyId, Long userId, Long paymentAttemptId) {
        post(groupBuyId, userId, "/payment-attempt", Map.of("paymentAttemptId", paymentAttemptId));
    }

    public void startConfirmation(Long groupBuyId, Long userId) {
        post(groupBuyId, userId, "/start-confirmation", null);
    }

    public void confirmationFailed(Long groupBuyId, Long userId) {
        post(groupBuyId, userId, "/confirmation-failed", null);
    }

    public void completeAdmission(Long groupBuyId, Long userId) {
        post(groupBuyId, userId, "/complete", null);
    }

    private void post(Long groupBuyId, Long userId, String suffix, Object body, Object... extraUriVars) {
        Object[] uriVars = new Object[2 + extraUriVars.length];
        uriVars[0] = groupBuyId;
        uriVars[1] = userId;
        System.arraycopy(extraUriVars, 0, uriVars, 2, extraUriVars.length);

        try {
            RestClient.RequestBodySpec request = restClient.post().uri(USER_PATH + suffix, uriVars);
            if (body != null) {
                request.body(body);
            }
            request.retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw toDomainException(exception);
        } catch (ResourceAccessException exception) {
            throw new CustomException(ErrorCode.QUEUE_UNAVAILABLE);
        }
    }

    /** 대기열 서비스가 알려준 ErrorCode 이름을 그대로 복원한다. 모르는 값이면 장애로 본다. */
    private CustomException toDomainException(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        for (ErrorCode candidate : ErrorCode.values()) {
            if (candidate.name().startsWith("QUEUE_") && body.contains(candidate.name())) {
                return new CustomException(candidate);
            }
        }
        return new CustomException(ErrorCode.QUEUE_UNAVAILABLE);
    }
}
