package com.gachisa.global.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * internal 경로는 대기열 서비스만 부를 수 있어야 한다. 브라우저에 열리면 남의 결제 시도를
 * 만료시킬 수 있다.
 *
 * <p>네트워크 격리가 1차 방어이고 이 필터는 그게 뚫렸을 때의 2차 방어다.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";

    private final byte[] expectedToken;

    public InternalTokenFilter(@Value("${queue.internal-token}") String internalToken) {
        this.expectedToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null
                || !MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedToken)) {
            // sendError는 /error 로 재디스패치되며 시큐리티를 다시 타 401로 바뀐다.
            // 원인을 알아보기 어려워지므로 여기서 직접 응답을 끝낸다.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("internal token required");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
