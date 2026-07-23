package com.choks.chokchok.web;

import com.choks.chokchok.service.AuthService;
import com.choks.chokchok.web.dto.LoginRequest;
import com.choks.chokchok.web.dto.MeResponse;
import com.choks.chokchok.web.dto.RefreshRequest;
import com.choks.chokchok.web.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인/토큰 재발급/내 정보. login·refresh는 공개, me는 인증 필요(SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(JwtAuthenticationToken authentication) {
        Jwt token = authentication.getToken();
        return new MeResponse(token.getSubject(), token.getClaimAsString("name"),
                token.getClaimAsString("role"), token.getClaimAsString("companyCode"),
                token.getClaimAsString("companyName"));
    }
}
