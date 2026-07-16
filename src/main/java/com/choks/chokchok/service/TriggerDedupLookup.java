package com.choks.chokchok.service;

import com.choks.chokchok.domain.Report;
import com.choks.chokchok.repository.ReportRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 트랜잭션이 UNIQUE 위반으로 rollback-only가 된 뒤 기존 리포트 id를 조회하기 위한 별도 빈.
 * REQUIRES_NEW로 오염된 컨텍스트를 정지시키고 깨끗한 트랜잭션에서 읽는다(별도 빈이어야 프록시 적용).
 */
@Service
class TriggerDedupLookup {

    private final ReportRepository reports;

    TriggerDedupLookup(ReportRepository reports) {
        this.reports = reports;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<Long> findExistingId(LocalDateTime triggerTime) {
        return reports.findByTriggerTime(triggerTime).map(Report::getId);
    }
}
