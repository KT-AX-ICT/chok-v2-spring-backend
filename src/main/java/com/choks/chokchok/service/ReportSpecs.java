package com.choks.chokchok.service;

import com.choks.chokchok.domain.Report;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/** 목록 동적 필터 (api-spec §4.1). status=DONE만 노출(v0.2.1). */
final class ReportSpecs {

    private ReportSpecs() {}

    static Specification<Report> doneOnly() {
        return (root, q, cb) -> cb.equal(root.get("status"), "DONE");
    }

    static Specification<Report> severity(String severity) {
        return (root, q, cb) -> cb.equal(root.get("severity"), severity);
    }

    // 날짜 필터 축 = detectedAt(=trigger_time, 장애 발생 시각). 저장 시각(createdAt) 아님.
    static Specification<Report> detectedFrom(LocalDateTime fromInclusiveUtc) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("triggerTime"), fromInclusiveUtc);
    }

    static Specification<Report> detectedBefore(LocalDateTime toExclusiveUtc) {
        return (root, q, cb) -> cb.lessThan(root.get("triggerTime"), toExclusiveUtc);
    }

    /** 통합 검색 — result.summary.highlight 또는 result.service LIKE (D-024: report에 service 컬럼 없어 JSON 파생). */
    static Specification<Report> searchLike(String search) {
        String pattern = "%" + escapeLike(search) + "%";
        return (root, q, cb) -> cb.or(
                cb.like(jsonValue(cb, root, "$.summary.highlight"), pattern, '\\'),
                cb.like(jsonValue(cb, root, "$.service"), pattern, '\\'));
    }

    private static jakarta.persistence.criteria.Expression<String> jsonValue(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<Report> root, String path) {
        return cb.function("json_value", String.class, root.get("result"), cb.literal(path));
    }

    /** LIKE 메타문자(\ % _)를 escape → 문자 그대로 매칭 (ESCAPE '\'). backslash를 먼저 치환. */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
