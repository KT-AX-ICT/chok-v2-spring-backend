package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Log;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogRepository extends JpaRepository<Log, Long> {
    long countByReportId(Long reportId);

    /** 상세 evidence.log.lines 원천 — 시간순 raw 행. */
    List<Log> findByReportIdOrderByTsAsc(Long reportId);
}
