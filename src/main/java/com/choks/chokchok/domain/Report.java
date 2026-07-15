package com.choks.chokchok.domain;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** RCA 리포트 (부모). schema.sql `report` 테이블과 1:1 매핑. */
@Entity
@Table(name = "report")
@Getter
@Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MVP는 항상 DONE (분석 완료 후 저장, D-023). 내부 전용 — 프론트 미노출. */
    @Column(length = 32, nullable = false)
    private String status;

    /** HIGH/MID/LOW. LLM 판정 연동 전까지 NULL 허용 (D-022). */
    @Column(length = 16)
    private String severity;

    private LocalDateTime windowFrom;

    private LocalDateTime windowTo;

    /** 트리거 발화 시각(UTC). detectedAt 원천 + 멱등키. UNIQUE(trigger_time). */
    @Column(nullable = false)
    private LocalDateTime triggerTime;

    /** {trigger_time, triggered_by[]} — 패스스루. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode triggerInfo;

    /** detail 5키 {rca, summary, evidence, impact, actions} — 패스스루. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode result;

    /** DB default CURRENT_TIMESTAMP(3)가 채움 — 앱은 읽기 전용. */
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
