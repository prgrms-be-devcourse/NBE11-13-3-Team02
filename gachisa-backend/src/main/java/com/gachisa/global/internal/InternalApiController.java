package com.gachisa.global.internal;

import com.gachisa.groupbuy.dto.GroupBuyQueueInfo;
import com.gachisa.groupbuy.service.GroupBuyService;
import com.gachisa.payment.service.PaymentAttemptExpiryService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 서비스(gachisa-queue)만 호출하는 서비스 간 API.
 *
 * <p>사용자 토큰이 아니라 공유 시크릿으로 인증한다([InternalTokenFilter]). 공개 API와
 * 같은 경로에 두면 실수로 노출되기 쉬워 internal 접두사로 분리했다.
 */
@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final GroupBuyService groupBuyService;
    private final PaymentAttemptExpiryService paymentAttemptExpiryService;

    /** 대기열이 입장 정원과 마감 여부를 판단하는 데 쓴다. */
    @GetMapping("/group-buys/{groupBuyId}/queue-info")
    public GroupBuyQueueInfo getQueueInfo(@PathVariable Long groupBuyId) {
        return groupBuyService.getQueueInfo(groupBuyId);
    }

    /** 대기열 입장이 만료되어 잡고 있던 결제 시도를 되돌려야 할 때 호출된다. */
    @PostMapping("/payment-attempts/{paymentAttemptId}/expire")
    public void expirePaymentAttempt(@PathVariable Long paymentAttemptId) {
        paymentAttemptExpiryService.expireIfReady(paymentAttemptId);
    }
}
