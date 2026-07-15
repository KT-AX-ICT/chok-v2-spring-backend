package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Report;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 멱등 판별 — 같은 trigger_time이면 이미 접수된 것. */
    Optional<Report> findByTriggerTime(LocalDateTime triggerTime);
}
