package com.gachisa.user.service;

import com.gachisa.auth.client.OAuthUserInfo;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.user.dto.UserInfo;
import com.gachisa.user.entity.User;
import com.gachisa.user.entity.UserProvider;
import com.gachisa.user.entity.UserRole;
import com.gachisa.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserInfo signUp(String email, String rawPassword, String name, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = User.builder()
            .email(email)
            .password(passwordEncoder.encode(rawPassword))
            .name(name)
            .role(role)
            .provider(UserProvider.LOCAL)
            .createdAt(LocalDateTime.now())
            .build();

        User saved = userRepository.save(user);
        return toUserInfo(saved);
    }

    public UserInfo authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        return toUserInfo(user);
    }

    public UserInfo getById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return toUserInfo(user);
    }

    @Transactional
    public UserInfo updateMe(Long userId, String name, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (name != null && !name.isBlank()) {
            user.updateName(name);
        }

        if (newPassword != null && !newPassword.isBlank()) {
            // 소셜 전용 계정(비밀번호 없음)은 현재 비밀번호 검증 없이 최초 비밀번호를 설정할 수 있게 한다.
            if (user.getPassword() != null && !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
            }
            user.changePassword(passwordEncoder.encode(newPassword));
        }

        return toUserInfo(user);
    }

    @Transactional
    public UserInfo findOrCreateOAuthUser(UserProvider provider, OAuthUserInfo oAuthUserInfo) {
        Optional<User> linked = userRepository.findByProviderAndProviderId(provider, oAuthUserInfo.providerId());
        if (linked.isPresent()) {
            return toUserInfo(linked.get());
        }

        String email = oAuthUserInfo.email();

        // 제공자가 이메일 인증을 확인해준 경우에만 기존 일반가입 계정에 자동으로 연동한다.
        if (oAuthUserInfo.emailVerified() && email != null) {
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                existing.get().linkOAuthAccount(provider, oAuthUserInfo.providerId());
                return toUserInfo(existing.get());
            }
        } else if (email != null && userRepository.existsByEmail(email)) {
            // 인증되지 않은 이메일이 이미 다른 계정에서 쓰이고 있다면, 그 계정을 가로채지 못하게 막는다.
            throw new CustomException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = User.builder()
            .email(email)
            .name(oAuthUserInfo.name())
            .role(UserRole.ROLE_BUYER)
            .provider(provider)
            .providerId(oAuthUserInfo.providerId())
            .createdAt(LocalDateTime.now())
            .build();

        return toUserInfo(userRepository.save(user));
    }

    private UserInfo toUserInfo(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getCreatedAt());
    }
}
