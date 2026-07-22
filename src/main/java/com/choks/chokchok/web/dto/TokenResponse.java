package com.choks.chokchok.web.dto;

/** 로그인/리프레시 응답. expiresIn은 액세스 토큰 만료까지 초. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
