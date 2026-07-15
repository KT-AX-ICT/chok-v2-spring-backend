package com.choks.chokchok.service;

/** 없는 id 또는 미완료(DONE 아님) id 조회 → 404 REPORT_NOT_FOUND. */
public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(Long id) {
        super("리포트를 찾을 수 없습니다: " + id);
    }
}
