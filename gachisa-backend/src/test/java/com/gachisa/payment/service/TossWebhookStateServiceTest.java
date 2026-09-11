package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import com.gachisa.payment.client.dto.PgPaymentQueryResult;
import com.gachisa.payment.entity.PaymentMethod;
import com.gachisa.payment.repository.TossWebhookEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TossWebhookStateServiceTest {

    @Mock
    private TossWebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRecoveryStateService recoveryStateService;

    @Mock
    private TimeProvider timeProvider;

    private TossWebhookStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new TossWebhookStateService(
                webhookEventRepository, recoveryStateService, timeProvider);
    }

    @Test
    void duplicateTransmissionIsIgnored() {
        given(webhookEventRepository.insertIfAbsent(
                ArgumentMatchers.eq("transmission-id"),
                ArgumentMatchers.eq("PAYMENT_STATUS_CHANGED"),
                ArgumentMatchers.eq("payment-key"),
                ArgumentMatchers.any(LocalDateTime.class)
        )).willReturn(0);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 14, 12, 0));

        boolean processed = stateService.apply(
                "transmission-id", "PAYMENT_STATUS_CHANGED", queryResult());

        assertThat(processed).isFalse();
        verify(recoveryStateService, never()).apply(ArgumentMatchers.anyLong(), ArgumentMatchers.any());
    }

    @Test
    void newTransmissionAppliesVerifiedStateAndStoresEvent() {
        PgPaymentQueryResult result = queryResult();
        given(webhookEventRepository.insertIfAbsent(
                ArgumentMatchers.eq("transmission-id"),
                ArgumentMatchers.eq("PAYMENT_STATUS_CHANGED"),
                ArgumentMatchers.eq("payment-key"),
                ArgumentMatchers.any(LocalDateTime.class)
        )).willReturn(1);
        given(recoveryStateService.findAttemptIdByPgOrderId("gachisa_order")).willReturn(1L);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 14, 12, 0));

        boolean processed = stateService.apply(
                "transmission-id", "PAYMENT_STATUS_CHANGED", result);

        assertThat(processed).isTrue();
        verify(recoveryStateService).apply(1L, result);
    }

    private PgPaymentQueryResult queryResult() {
        return new PgPaymentQueryResult(
                "payment-key", "gachisa_order", 12_600, "DONE", PaymentMethod.CARD,
                null, null, 0);
    }
}
