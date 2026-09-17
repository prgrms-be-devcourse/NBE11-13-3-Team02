package com.gachisa.order.controller

import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.order.entity.DeliveryStatus
import com.gachisa.order.service.OrderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminOrderController::class)
@Import(AdminOrderControllerTest.MethodSecurityConfig::class)
class AdminOrderControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var orderService: OrderService
    @MockitoBean private lateinit var jwtTokenProvider: JwtTokenProvider
    @Test @WithMockUser(roles = ["BUYER"])
    fun buyerCannotChangeDeliveryStatus() {
        mockMvc.perform(patch("/api/admin/orders/018330029/delivery-status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryStatus\":\"SHIPPING\"}"))
            .andExpect { result -> assertThat(result.resolvedException).isInstanceOf(AuthorizationDeniedException::class.java) }
        verifyNoInteractions(orderService)
    }
    @Test @WithMockUser(roles = ["ADMIN"])
    fun adminCanChangeDeliveryStatus() {
        mockMvc.perform(patch("/api/admin/orders/018330029/delivery-status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryStatus\":\"SHIPPING\"}")).andExpect(status().isOk)
        verify(orderService).updateDeliveryStatusByAdmin("018330029", DeliveryStatus.SHIPPING)
    }
    @EnableMethodSecurity class MethodSecurityConfig
}
