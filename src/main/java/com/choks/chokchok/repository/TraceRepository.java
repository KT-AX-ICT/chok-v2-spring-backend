package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Trace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceRepository extends JpaRepository<Trace, Long> {
    long countByReportId(Long reportId);

    /** 상세 evidence.trace.spans 원천 — 시간순 raw 행. */
    List<Trace> findByReportIdOrderByTsAsc(Long reportId);
}
