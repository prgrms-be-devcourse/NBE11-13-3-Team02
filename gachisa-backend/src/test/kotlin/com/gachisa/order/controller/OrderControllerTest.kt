package com.gachisa.order.controller

import com.gachisa.global.security.AuthenticatedUser
import com.gachisa.global.security.CustomUserDetails
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.order.service.OrderService
import com.gachisa.user.entity.UserRole
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(OrderController::class)
class OrderControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var orderService: OrderService
    @MockitoBean private lateinit var jwtTokenProvider: JwtTokenProvider

    @Test
    fun authenticatedBuyerCanReadOrder() {
        val buyer = CustomUserDetails(AuthenticatedUser(30L, "buyer@test.com", UserRole.ROLE_BUYER))
        mockMvc.perform(get("/api/orders/1").with(user(buyer))).andExpect(status().isOk)
        verify(orderService).getMyOrder(1L, 30L)
    }

    @Test
    fun invalidDeliveryAddressReturnsBadRequest() {
        val buyer = CustomUserDetails(AuthenticatedUser(30L, "buyer@test.com", UserRole.ROLE_BUYER))
        mockMvc.perform(post("/api/orders/1/delivery-address").with(user(buyer)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"recipientName":"구매자","recipientPhone":"잘못된 번호","zipCode":"123","address":"서울시","addressDetail":"101호"}"""))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(orderService)
    }
}
