package com.choks.chokchok.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.choks.chokchok.domain.Company;
import com.choks.chokchok.domain.User;
import com.choks.chokchok.repository.UserRepository;
import com.choks.chokchok.web.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 보안 경로 검증: 비번 매칭(시드 $2y 해시 호환)·토큰 클레임·리프레시 type 가드. Docker 불필요. */
class AuthServiceTest {

    // seed-dev.sql의 실제 해시 — 평문 "chokchok1!" ($2y). Spring BCrypt가 $2y를 검증하는지까지 확인.
    private static final String SEED_HASH =
            "$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.";
    private static final String EMAIL = "admin@chokchok.dev";

    private UserRepository users;
    private AuthService auth;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(
                "test-secret-test-secret-test-secret-32b!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).build();
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtService jwt = new JwtService(encoder, 60, 14);
        users = mock(UserRepository.class);
        auth = new AuthService(users, new BCryptPasswordEncoder(), jwt, decoder);
    }

    private User seedUser() {
        Company company = new Company();
        company.setCompanyCode("CHOK");
        company.setCompanyName("촉촉 주식회사");
        User user = new User();
        user.setEmail(EMAIL);
        user.setName("관리자");
        user.setRole("ADMIN");
        user.setPasswordHash(SEED_HASH);
        user.setCompany(company);
        return user;
    }

    @Test
    void login_success_issuesTokensWithClaims() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(seedUser()));

        TokenResponse res = auth.login(EMAIL, "chokchok1!");

        assertNotNull(res.accessToken());
        assertNotNull(res.refreshToken());
        assertEquals("Bearer", res.tokenType());

        Jwt access = decoder.decode(res.accessToken());
        assertEquals(EMAIL, access.getSubject());
        assertEquals("ADMIN", access.getClaimAsString("role"));
        assertEquals("CHOK", access.getClaimAsString("companyCode"));
        assertEquals("촉촉 주식회사", access.getClaimAsString("companyName"));
        assertEquals("access", access.getClaimAsString("type"));
        assertEquals("refresh", decoder.decode(res.refreshToken()).getClaimAsString("type"));
    }

    @Test
    void login_wrongPassword_rejected() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(seedUser()));
        assertThrows(InvalidCredentialsException.class, () -> auth.login(EMAIL, "wrong"));
    }

    @Test
    void login_unknownEmail_rejected() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> auth.login("nope@x.dev", "chokchok1!"));
    }

    @Test
    void refresh_withRefreshToken_reissues() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(seedUser()));
        TokenResponse first = auth.login(EMAIL, "chokchok1!");

        TokenResponse again = auth.refresh(first.refreshToken());

        assertEquals("access", decoder.decode(again.accessToken()).getClaimAsString("type"));
    }

    @Test
    void refresh_rejectsAccessTokenInRefreshSlot() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(seedUser()));
        TokenResponse first = auth.login(EMAIL, "chokchok1!");

        // 액세스 토큰을 리프레시로 사용 시도 → type 가드가 막아야 함
        assertThrows(InvalidCredentialsException.class, () -> auth.refresh(first.accessToken()));
    }
}
