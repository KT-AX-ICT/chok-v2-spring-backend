package com.choks.chokchok.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** #1 회귀 방지: 리소스 서버 검증기가 type=access만 통과시키고 refresh 토큰은 거부하는지 확인. */
class AccessTokenValidatorTest {

    private static final SecretKey KEY = new SecretKeySpec(
            "test-secret-test-secret-test-secret-32b!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(KEY).build();
    private final JwtDecoder plainDecoder =
            NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();

    private Jwt token(String type) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("admin@chokchok.dev").issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(60)))
                .claim("type", type).build();
        String value = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return plainDecoder.decode(value);   // 서명·만료만 통과한 원시 토큰
    }

    @Test
    void accessToken_passes() {
        assertFalse(SecurityConfig.accessTokenValidator().validate(token("access")).hasErrors());
    }

    @Test
    void refreshToken_rejected() {
        assertTrue(SecurityConfig.accessTokenValidator().validate(token("refresh")).hasErrors());
    }
}
