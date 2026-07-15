package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Log;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogRepository extends JpaRepository<Log, Long> {
}
