package com.choks.chokchok.service;

/** 로그인/리프레시 실패. 계정 존재 여부를 노출하지 않도록 사유별로 세분하지 않는다. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
