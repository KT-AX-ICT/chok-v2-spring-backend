package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Metric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricRepository extends JpaRepository<Metric, Long> {
    long countByReportId(Long reportId);
}
