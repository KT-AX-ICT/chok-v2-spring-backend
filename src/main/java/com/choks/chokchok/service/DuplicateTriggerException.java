package com.choks.chokchok.service;

/** 같은 trigger_time 재전송 → 409. 기존 report id를 함께 전달. */
public class DuplicateTriggerException extends RuntimeException {
    private final Long reportId;

    public DuplicateTriggerException(Long reportId) {
        super("이미 접수된 트리거입니다");
        this.reportId = reportId;
    }

    public Long getReportId() {
        return reportId;
    }
}
