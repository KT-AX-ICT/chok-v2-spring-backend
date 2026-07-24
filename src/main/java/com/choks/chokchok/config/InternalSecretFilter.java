package com.choks.chokchok.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/internal/** 서버-투-서버 요청을 공유 시크릿 헤더로 검증(D-013 후속).
 * FastAPI가 X-Internal-Secret 헤더에 시크릿을 담아 보내고, 값이 일치할 때만 통과.
 * 사용자 JWT 대상이 아닌 서버 간 호출이라 인증 대신 공유 시크릿을 쓴다.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Secret";
    private final byte[] expected;

    InternalSecretFilter(String secret) {
        this.expected = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** /api/internal/** 이외의 경로는 이 필터가 관여하지 않음. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String provided = req.getHeader(HEADER);
        // MessageDigest.isEqual = 상수 시간 비교(타이밍 공격 방지). 헤더 없으면 즉시 거절.
        if (provided == null
                || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다\"}}");
            return;
        }
        chain.doFilter(req, res);
    }
}
