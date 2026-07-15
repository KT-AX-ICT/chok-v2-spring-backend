package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Trace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceRepository extends JpaRepository<Trace, Long> {
}
