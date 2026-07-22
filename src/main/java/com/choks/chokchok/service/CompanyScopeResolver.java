package com.choks.chokchok.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 현재 요청의 회사 격리 범위. ADMIN은 전역(all), 그 외는 토큰 companyCode로 한정.
 * 신원은 JWT 클레임에서만 읽는다(DB 재조회 없음).
 */
@Component
public class CompanyScopeResolver {

    /** all=true면 전 회사, 아니면 companyCode 한정. companyCode가 null이면 아무 행도 매칭 안 됨(deny). */
    public record Scope(boolean all, String companyCode) {}

    public Scope current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String role = jwt.getToken().getClaimAsString("role");
            if ("ADMIN".equals(role)) {
                return new Scope(true, null);
            }
            return new Scope(false, jwt.getToken().getClaimAsString("companyCode"));
        }
        // 인증 컨텍스트 없음(정상 흐름에선 필터가 먼저 막음) — 방어적으로 아무것도 못 보게.
        return new Scope(false, null);
    }
}
