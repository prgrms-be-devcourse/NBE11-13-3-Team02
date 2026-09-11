package com.gachisa.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachisa.global.security.AuthenticatedUser;
import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.order.service.OrderService;
import com.gachisa.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OrderService orderService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedBuyerCanReadOrder() throws Exception {
        CustomUserDetails buyer = new CustomUserDetails(
                new AuthenticatedUser(30L, "buyer@test.com", UserRole.ROLE_BUYER));

        mockMvc.perform(get("/api/orders/1").with(user(buyer)))
                .andExpect(status().isOk());

        verify(orderService).getMyOrder(1L, 30L);
    }

    @Test
    void invalidDeliveryAddressReturnsBadRequest() throws Exception {
        CustomUserDetails buyer = new CustomUserDetails(
                new AuthenticatedUser(30L, "buyer@test.com", UserRole.ROLE_BUYER));

        mockMvc.perform(post("/api/orders/1/delivery-address")
                        .with(user(buyer))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "구매자",
                                  "recipientPhone": "잘못된 번호",
                                  "zipCode": "123",
                                  "address": "서울시",
                                  "addressDetail": "101호"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).registerDeliveryAddress(any(), any(), any());
    }
}
