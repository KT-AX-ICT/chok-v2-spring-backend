package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
