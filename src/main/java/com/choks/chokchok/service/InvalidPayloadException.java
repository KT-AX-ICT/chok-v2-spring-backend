package com.choks.chokchok.service;

/** 필수 필드 누락·timestamp 형식 오류 등 → 422. */
public class InvalidPayloadException extends RuntimeException {
    public InvalidPayloadException(String message) {
        super(message);
    }
}
