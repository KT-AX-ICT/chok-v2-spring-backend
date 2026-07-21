package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Metric;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricRepository extends JpaRepository<Metric, Long> {
    long countByReportId(Long reportId);

    /** 상세 evidence.metric.items 원천 — 시간순 raw 행. */
    List<Metric> findByReportIdOrderByTsAsc(Long reportId);
}
