package com.choks.chokchok.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 격리 범위 계산: ADMIN=전역, USER=자사 코드, 비인증=deny. */
class CompanyScopeResolverTest {

    private final CompanyScopeResolver resolver = new CompanyScopeResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role, String companyCode) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256")
                .subject("u@x.dev").claim("role", role).claim("companyCode", companyCode).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void admin_isGlobal() {
        authenticateAs("ADMIN", "CHOK");
        CompanyScopeResolver.Scope s = resolver.current();
        assertTrue(s.all());
        assertNull(s.companyCode());
    }

    @Test
    void user_isScopedToOwnCompany() {
        authenticateAs("USER", "SN001");
        CompanyScopeResolver.Scope s = resolver.current();
        assertFalse(s.all());
        assertEquals("SN001", s.companyCode());
    }

    @Test
    void noAuth_deniesAll() {
        CompanyScopeResolver.Scope s = resolver.current();
        assertFalse(s.all());
        assertNull(s.companyCode()); // company(null) spec → 매칭 0건
    }
}
