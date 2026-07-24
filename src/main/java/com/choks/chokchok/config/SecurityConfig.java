package com.choks.chokchok.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * JWT(HS256 대칭키) 리소스서버 보안. 모든 /api/** 는 인증 필요하되
 * 로그인·리프레시(공개)와 internal 수집(서버간 호출)은 permitAll.
 * CORS는 여기 CorsConfigurationSource로 일원화(기존 WebMvcConfigurer CorsConfig 대체).
 */
@Configuration
public class SecurityConfig {

    private final String secret;
    private final String internalSecret;
    private final String[] allowedOrigins;

    public SecurityConfig(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.internal.shared-secret}") String internalSecret,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.secret = secret;
        this.internalSecret = internalSecret;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        // internal 수집은 서버-투-서버(FastAPI→Spring). JWT 대신 InternalSecretFilter가 공유시크릿 검증.
                        .requestMatchers("/api/internal/**").permitAll()
                        .anyRequest().authenticated())
                // TODO(임시): FastAPI 송신측 X-Internal-Secret 헤더 미구현 → 검증 비활성화. 연동되면 주석 해제.
                // .addFilterBefore(new InternalSecretFilter(internalSecret),
                //         UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** 리소스 서버 전용 디코더: 표준(서명·만료) 검증 + type=access 강제. refresh 토큰의 일반 API 사용 차단. */
    private JwtDecoder accessTokenDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(accessTokenValidator());
        return decoder;
    }

    /** 표준 검증기 + "type 클레임이 access여야 함" 검증기 결합. */
    static OAuth2TokenValidator<Jwt> accessTokenValidator() {
        OAuth2TokenValidator<Jwt> requireAccessType = token ->
                "access".equals(token.getClaimAsString("type"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "access 토큰이 아닙니다", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), requireAccessType);
    }

    private SecretKey key() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(key()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.asList(allowedOrigins));
        cfg.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    /** role 클레임("ADMIN"/"USER")을 ROLE_* 권한으로 매핑. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("ROLE_");
        authorities.setAuthoritiesClaimName("role");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** 토큰 없음/무효 → 401. 기존 에러 봉투 형식으로 출력. */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (req, res, ex) ->
                writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다");
    }

    /** 권한 부족 → 403. */
    private AccessDeniedHandler accessDeniedHandler() {
        return (req, res, ex) ->
                writeError(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다");
    }

    private static void writeError(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
    }
}
