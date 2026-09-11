package com.gachisa.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachisa.global.security.AuthenticatedUser;
import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.user.dto.UserInfo;
import com.gachisa.user.entity.UserRole;
import com.gachisa.user.service.UserService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    private CustomUserDetails buyer() {
        return new CustomUserDetails(new AuthenticatedUser(1L, "buyer@test.com", UserRole.ROLE_BUYER));
    }

    @Test
    void updateMeWithTooShortNewPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .with(user(buyer()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "1234", "newPassword": "123"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateMe(any(), any(), any(), any());
    }

    @Test
    void updateMeWithOnlyNameFieldIsAccepted() throws Exception {
        given(userService.updateMe(1L, "새이름", null, null))
                .willReturn(new UserInfo(1L, "buyer@test.com", "새이름", UserRole.ROLE_BUYER, LocalDateTime.now()));

        mockMvc.perform(patch("/api/users/me")
                        .with(user(buyer()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "새이름"}
                                """))
                .andExpect(status().isOk());

        verify(userService).updateMe(1L, "새이름", null, null);
    }
}
