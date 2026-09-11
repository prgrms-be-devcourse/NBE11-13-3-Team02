package com.gachisa.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.order.entity.DeliveryStatus;
import com.gachisa.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminOrderController.class)
@Import(AdminOrderControllerTest.MethodSecurityConfig.class)
class AdminOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OrderService orderService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(roles = "BUYER")
    void buyerCannotChangeDeliveryStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/018330029/delivery-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryStatus\":\"SHIPPING\"}"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(AuthorizationDeniedException.class));

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanChangeDeliveryStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/018330029/delivery-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryStatus\":\"SHIPPING\"}"))
                .andExpect(status().isOk());

        verify(orderService).updateDeliveryStatusByAdmin("018330029", DeliveryStatus.SHIPPING);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
