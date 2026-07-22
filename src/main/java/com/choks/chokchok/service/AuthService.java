package com.choks.chokchok.service;

import com.choks.chokchok.domain.User;
import com.choks.chokchok.repository.UserRepository;
import com.choks.chokchok.web.dto.TokenResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 인증 + 토큰 발급/재발급. company(LAZY) 접근을 위해 트랜잭션 안에서 토큰을 만든다. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final JwtDecoder jwtDecoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
            JwtService jwt, JwtDecoder jwtDecoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.jwtDecoder = jwtDecoder;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String rawPassword) {
        User user = users.findByEmail(email)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Jwt decoded;
        try {
            decoded = jwtDecoder.decode(refreshToken);
        } catch (JwtException e) {
            throw new InvalidCredentialsException("유효하지 않은 토큰입니다");
        }
        if (!"refresh".equals(decoded.getClaimAsString("type"))) {
            throw new InvalidCredentialsException("리프레시 토큰이 아닙니다");
        }
        User user = users.findByEmail(decoded.getSubject())
                .orElseThrow(() -> new InvalidCredentialsException("계정을 찾을 수 없습니다"));
        return tokens(user);
    }

    private TokenResponse tokens(User user) {
        return new TokenResponse(
                jwt.issueAccess(user), jwt.issueRefresh(user), "Bearer", jwt.accessTtlSeconds());
    }
}
