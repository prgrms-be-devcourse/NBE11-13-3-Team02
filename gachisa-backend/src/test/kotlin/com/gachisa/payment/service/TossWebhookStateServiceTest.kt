package com.gachisa.payment.service

import com.gachisa.global.util.TimeProvider
import com.gachisa.payment.client.dto.PgPaymentQueryResult
import com.gachisa.payment.entity.PaymentMethod
import com.gachisa.payment.repository.TossWebhookEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class TossWebhookStateServiceTest {
    @Mock private lateinit var webhookEventRepository: TossWebhookEventRepository
    @Mock private lateinit var recoveryStateService: PaymentRecoveryStateService
    @Mock private lateinit var timeProvider: TimeProvider
    private lateinit var stateService: TossWebhookStateService
    @BeforeEach fun setUp() { stateService = TossWebhookStateService(webhookEventRepository, recoveryStateService, timeProvider) }
    @Test fun duplicateTransmissionIsIgnored() {
        given(webhookEventRepository.insertIfAbsent("transmission-id", "PAYMENT_STATUS_CHANGED", "payment-key", NOW)).willReturn(0)
        given(timeProvider.now()).willReturn(NOW)
        assertThat(stateService.apply("transmission-id", "PAYMENT_STATUS_CHANGED", queryResult())).isFalse()
        verifyNoInteractions(recoveryStateService)
    }
    @Test fun newTransmissionAppliesVerifiedStateAndStoresEvent() {
        val result = queryResult()
        given(webhookEventRepository.insertIfAbsent("transmission-id", "PAYMENT_STATUS_CHANGED", "payment-key", NOW)).willReturn(1)
        given(recoveryStateService.findAttemptIdByPgOrderId("gachisa_order")).willReturn(1L)
        given(timeProvider.now()).willReturn(NOW)
        assertThat(stateService.apply("transmission-id", "PAYMENT_STATUS_CHANGED", result)).isTrue()
        verify(recoveryStateService).apply(1L, result)
    }
    private fun queryResult() = PgPaymentQueryResult("payment-key", "gachisa_order", 12_600, "DONE", PaymentMethod.CARD, null, null, 0)
    companion object { private val NOW = LocalDateTime.of(2026, 8, 14, 12, 0) }
}
