package com.gachisa.global.security;

import com.gachisa.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// TODO(인증 담당자): 실제 인증 흐름(JwtAuthenticationFilter)에서 이 객체를 생성해 SecurityContext에 저장.
// GB/PT 컨트롤러는 @AuthenticationPrincipal CustomUserDetails 로 이 객체를 주입받아 사용한다.
@Getter
public class CustomUserDetails implements UserDetails {

    private final AuthenticatedUser authenticatedUser;
    private final Long userId;
    private final String email;
    private final String role;

    public CustomUserDetails(User user) {
        this.authenticatedUser = null;
        this.userId = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole().name();
    }

    public CustomUserDetails(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
        this.userId = authenticatedUser.id();
        this.email = authenticatedUser.name();
        this.role = authenticatedUser.role().name().replaceFirst("^ROLE_", "");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null; // 인증 이후 비밀번호는 노출하지 않음
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
