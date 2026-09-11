package com.gachisa.global.security;

import com.gachisa.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;
    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
        this.jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    public String createAccessToken(Long userId, String name, UserRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.accessTokenValidity().toMillis());

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(expiry)
            .claim(CLAIM_NAME, name)
            .claim(CLAIM_ROLE, role.name())
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact();
    }

    public TokenStatus validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);
            return TokenStatus.VALID;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰입니다.");
            return TokenStatus.EXPIRED;
        } catch (Exception e) {
            log.debug("유효하지 않은 토큰입니다.");
            return TokenStatus.INVALID;
        }
    }

    public AuthenticatedUser getTokenDetails(String token) {
        Claims claims = getClaims(token);

        return new AuthenticatedUser(
            Long.valueOf(claims.getSubject()),
            claims.get(CLAIM_NAME, String.class),
            UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))
        );
    }

    public Authentication getAuthentication(String token) {
        AuthenticatedUser authenticatedUser = getTokenDetails(token);
        CustomUserDetails principal = new CustomUserDetails(authenticatedUser);

        return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
    }

    private Claims getClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

}
