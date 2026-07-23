package com.choks.chokchok.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.choks.chokchok.domain.Company;
import com.choks.chokchok.domain.User;
import com.choks.chokchok.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * #1 회귀 방지(엔드투엔드): 리소스 서버 체인에 type=access 검증기가 실제로 연결됐는지 확인.
 * refresh 토큰을 Bearer로 보호 API에 제출하면 401이어야 함. 단위 테스트(AccessTokenValidatorTest)가
 * 못 잡는 "필터체인 배선 누락" 회귀를 잡는다. /api/auth/me는 클레임만 읽어 DB 불필요.
 */
@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-32b!")
@AutoConfigureMockMvc
@Testcontainers
class RefreshTokenRejectionTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    private User user() {
        Company company = new Company();
        company.setCompanyCode("CHOK");
        company.setCompanyName("CHOK");
        User user = new User();
        user.setEmail("admin@chokchok.dev");
        user.setName("관리자");
        user.setRole("ADMIN");
        user.setCompany(company);
        return user;
    }

    @Test
    void refreshToken_onProtectedApi_isUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt.issueRefresh(user())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessToken_onProtectedApi_isOk() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt.issueAccess(user())))
                .andExpect(status().isOk());
    }
}
