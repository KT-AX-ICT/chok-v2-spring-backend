package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Report;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository
        extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    /** 멱등 판별 — 같은 trigger_time이면 이미 접수된 것. */
    Optional<Report> findByTriggerTime(LocalDateTime triggerTime);

    /** 상세 — DONE만 노출 (v0.2.1). */
    Optional<Report> findByIdAndStatus(Long id, String status);

    /** 대시보드 KPI. */
    long countByStatus(String status);

    long countByStatusAndSeverity(String status, String severity);

    /** todayCount — 오늘(KST) 발생(detectedAt=trigger_time) 기준. */
    long countByStatusAndTriggerTimeGreaterThanEqualAndTriggerTimeLessThan(
            String status, LocalDateTime fromInclusive, LocalDateTime toExclusive);

    /** 대시보드 최근 5건 (createdAt desc). */
    List<Report> findTop5ByStatusOrderByCreatedAtDesc(String status);
}
