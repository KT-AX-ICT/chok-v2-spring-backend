package com.choks.chokchok.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** InternalSecretFilter: 올바른 시크릿만 통과, 없거나 틀리면 401. /api/internal 밖은 미검사. */
class InternalSecretFilterTest {

    private static final String SECRET = "s3cr3t-shared-value";
    private final InternalSecretFilter filter = new InternalSecretFilter(SECRET);

    private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return res;
    }

    private MockHttpServletRequest internalRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/internal/reports");
        req.setRequestURI("/api/internal/reports");
        return req;
    }

    @Test
    void validSecret_passes() throws Exception {
        MockHttpServletRequest req = internalRequest();
        req.addHeader(InternalSecretFilter.HEADER, SECRET);
        MockHttpServletResponse res = run(req);
        // 통과 시 필터가 응답에 손대지 않음(체인으로 넘어감) → 기본 200
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void wrongSecret_rejected() throws Exception {
        MockHttpServletRequest req = internalRequest();
        req.addHeader(InternalSecretFilter.HEADER, "wrong");
        assertThat(run(req).getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void missingHeader_rejected() throws Exception {
        assertThat(run(internalRequest()).getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void nonInternalPath_notFiltered() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
        req.setRequestURI("/api/auth/me");
        // 시크릿 헤더 없어도 shouldNotFilter=true → 체인 통과(200)
        assertThat(run(req).getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
