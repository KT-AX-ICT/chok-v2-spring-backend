package com.choks.chokchok.service;

import com.choks.chokchok.domain.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** HS256 대칭키로 JWT를 발급. 검증은 Spring Security 리소스서버(JwtDecoder)가 담당. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public JwtService(JwtEncoder encoder,
            @Value("${app.jwt.access-ttl-minutes:60}") long accessTtlMinutes,
            @Value("${app.jwt.refresh-ttl-days:14}") long refreshTtlDays) {
        this.encoder = encoder;
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String issueAccess(User user) {
        return issue(user, "access", Duration.ofMinutes(accessTtlMinutes));
    }

    public String issueRefresh(User user) {
        // ponytail: 무상태 리프레시 JWT — 만료 전 강제 폐기 불가가 천장. 필요 시 저장형 테이블로 승급.
        return issue(user, "refresh", Duration.ofDays(refreshTtlDays));
    }

    public long accessTtlSeconds() {
        return accessTtlMinutes * 60;
    }

    private String issue(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("type", type)
                .claim("role", user.getRole())
                .claim("name", user.getName())
                .claim("companyCode", user.getCompany().getCompanyCode())
                .claim("companyName", user.getCompany().getCompanyName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
