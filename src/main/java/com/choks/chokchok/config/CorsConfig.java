package com.choks.chokchok.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트 개발 서버(Vite localhost:5173)·배포 도메인에서 브라우저가 /api 를 호출할 수 있도록 CORS 허용.
 * Spring Security 미도입(인증 없음, D-013)이라 WebMvcConfigurer 전역 CORS로 충분.
 * 허용 origin은 app.cors.allowed-origins 프로퍼티로 주입 — 배포 도메인 확정 시 코드 변경 없이 yaml만 추가.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // MVP는 조회 GET·저장 POST만. 인증 없어 credentials 불필요. internal(FastAPI→Spring)은 서버간 호출이라 CORS 무관.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
