package com.gachisa.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.user.dto.UserInfo;
import com.gachisa.user.entity.User;
import com.gachisa.user.entity.UserRole;
import com.gachisa.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void getByIdReturnsUserInfo() {
        User user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        UserInfo userInfo = userService.getById(USER_ID);

        assertThat(userInfo.id()).isEqualTo(USER_ID);
        assertThat(userInfo.email()).isEqualTo("buyer1@test.com");
        assertThat(userInfo.name()).isEqualTo("구매자1");
        assertThat(userInfo.role()).isEqualTo(UserRole.ROLE_BUYER);
    }

    @Test
    void getByIdThrowsWhenUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateMeChangesNameOnly() {
        User user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        UserInfo userInfo = userService.updateMe(USER_ID, "새이름", null, null);

        assertThat(userInfo.name()).isEqualTo("새이름");
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateMeChangesPasswordWhenCurrentPasswordMatches() {
        User user = user("buyer1@test.com", "encoded-old-password", "구매자1", UserRole.ROLE_BUYER);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old-password", "encoded-old-password")).willReturn(true);
        given(passwordEncoder.encode("new-password")).willReturn("encoded-new-password");

        userService.updateMe(USER_ID, null, "old-password", "new-password");

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
    }

    @Test
    void updateMeThrowsWhenCurrentPasswordDoesNotMatch() {
        User user = user("buyer1@test.com", "encoded-old-password", "구매자1", UserRole.ROLE_BUYER);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encoded-old-password")).willReturn(false);

        assertThatThrownBy(() -> userService.updateMe(USER_ID, null, "wrong-password", "new-password"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    private User user(String email, String password, String name, UserRole role) {
        User user = User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(role)
                .createdAt(NOW)
                .build();
        ReflectionTestUtils.setField(user, "id", UserServiceTest.USER_ID);
        return user;
    }
}
