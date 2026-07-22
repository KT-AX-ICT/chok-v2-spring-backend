package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Report;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository
        extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    /** 멱등 판별 — 같은 trigger_time이면 이미 접수된 것. */
    Optional<Report> findByTriggerTime(LocalDateTime triggerTime);

    /** 상세 — DONE만 노출 (v0.2.1). */
    Optional<Report> findByIdAndStatus(Long id, String status);
}
