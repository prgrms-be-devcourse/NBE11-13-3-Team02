package com.gachisa.dev;

import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.user.entity.User;
import com.gachisa.user.entity.UserProvider;
import com.gachisa.user.entity.UserRole;
import com.gachisa.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * k6 대기열 테스트를 위한 로컬 전용 데이터 생성기입니다.
 * local 프로필 이외에는 빈 자체가 등록되지 않으므로 운영 환경에 노출되지 않습니다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/dev/load-test")
@RequiredArgsConstructor
public class QueueLoadTestFixtureController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/buyers")
    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public QueueLoadTestUsersResponse createBuyers(@Valid @RequestBody QueueLoadTestUsersRequest request) {
        // 같은 로컬 테스트 암호는 한 번만 해시해 대량 생성 준비 시간을 줄입니다.
        String encodedPassword = passwordEncoder.encode(request.password());
        LocalDateTime now = LocalDateTime.now();
        List<User> buyers = new ArrayList<>();

        for (int index = 1; index <= request.userCount(); index++) {
            String email = request.emailPrefix() + "-" + index + "@test.local";
            buyers.add(User.of(
                    email,
                    encodedPassword,
                    "대기열부하테스트" + index,
                    UserRole.ROLE_BUYER,
                    UserProvider.LOCAL,
                    null,
                    now));
        }

        List<User> savedBuyers = userRepository.saveAll(buyers);
        userRepository.flush();

        List<QueueLoadTestUser> users = new ArrayList<>();
        for (int index = 0; index < savedBuyers.size(); index++) {
            User buyer = savedBuyers.get(index);
            users.add(new QueueLoadTestUser(
                    index + 1,
                    buyer.getEmail(),
                    jwtTokenProvider.createAccessToken(buyer.getId(), buyer.getName(), buyer.getRole())));
        }
        return new QueueLoadTestUsersResponse(users);
    }

    public record QueueLoadTestUsersRequest(
            @Min(1) @Max(3000) int userCount,
            @NotBlank String emailPrefix,
            @NotBlank String password
    ) {
    }

    public record QueueLoadTestUsersResponse(List<QueueLoadTestUser> users) {
    }

    public record QueueLoadTestUser(int index, String email, String accessToken) {
    }
}
