package com.choks.chokchok.domain;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * log/metric/trace 3종 공통 골격 (schema.sql 동일 구조: id·report_id·ts·service·raw).
 * DB 컬럼 `ts`는 유지 — 전송 JSON 필드명 `timestamp`와의 매핑은 DTO 몫 (D-022).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SignalRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(nullable = false)
    private LocalDateTime ts;

    @Column(length = 128, nullable = false)
    private String service;

    /** 원본 한 줄 그대로 (무파싱 TEXT, D-021). */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String raw;
}
