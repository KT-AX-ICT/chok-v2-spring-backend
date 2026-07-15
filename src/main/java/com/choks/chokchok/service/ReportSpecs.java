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

    static Specification<Report> createdFrom(LocalDateTime fromInclusiveUtc) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromInclusiveUtc);
    }

    static Specification<Report> createdBefore(LocalDateTime toExclusiveUtc) {
        return (root, q, cb) -> cb.lessThan(root.get("createdAt"), toExclusiveUtc);
    }

    /** summary(=result.summary.highlight) LIKE — JSON 경로 추출 (7/14 D-023: title 제거로 summary 단독). */
    static Specification<Report> summaryLike(String search) {
        return (root, q, cb) -> cb.like(
                cb.function("json_value", String.class, root.get("result"),
                        cb.literal("$.summary.highlight")),
                "%" + search + "%");
    }
}
