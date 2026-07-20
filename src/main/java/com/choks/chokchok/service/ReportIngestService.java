package com.choks.chokchok.service;

import com.choks.chokchok.domain.Log;
import com.choks.chokchok.domain.Metric;
import com.choks.chokchok.domain.Report;
import com.choks.chokchok.domain.SignalRow;
import com.choks.chokchok.domain.Trace;
import com.choks.chokchok.repository.LogRepository;
import com.choks.chokchok.repository.MetricRepository;
import com.choks.chokchok.repository.ReportRepository;
import com.choks.chokchok.repository.TraceRepository;
import com.choks.chokchok.support.Timestamps;
import com.choks.chokchok.web.IngestRequest;
import com.choks.chokchok.web.IngestRequest.SignalItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분석 완료 번들+결과를 report + 3종으로 한 트랜잭션에 저장 (SVC-01, api-spec §5.1). */
@Service
public class ReportIngestService {

    private static final Logger log = LoggerFactory.getLogger(ReportIngestService.class);
    private static final int SERVICE_MAX = 128;  // schema: service VARCHAR(128)

    private final ReportRepository reports;
    private final LogRepository logs;
    private final MetricRepository metrics;
    private final TraceRepository traces;
    private final TriggerDedupLookup dedup;

    public ReportIngestService(ReportRepository reports, LogRepository logs,
                               MetricRepository metrics, TraceRepository traces,
                               TriggerDedupLookup dedup) {
        this.reports = reports;
        this.logs = logs;
        this.metrics = metrics;
        this.traces = traces;
        this.dedup = dedup;
    }

    @Transactional
    public Long save(IngestRequest req) {
        validate(req);

        LocalDateTime triggerTime =
                Timestamps.parseUtc(req.triggerInfo().get("triggerTime").asString());

        // 멱등: 선(先)체크로 흔한 재전송을 빠르게 걸러냄
        reports.findByTriggerTime(triggerTime).ifPresent(r -> {
            throw new DuplicateTriggerException(r.getId());
        });

        Report report = new Report();
        report.setStatus(req.status());
        report.setSeverity(req.severity());                 // D-022: NULL 허용
        if (req.window() != null) {
            report.setWindowFrom(Timestamps.parseUtcNullable(req.window().start()));
            report.setWindowTo(Timestamps.parseUtcNullable(req.window().end()));
        }
        report.setTriggerTime(triggerTime);
        report.setTriggerInfo(req.triggerInfo());           // JSON 패스스루
        report.setResult(req.result());                     // JSON 패스스루

        try {
            reports.saveAndFlush(report);                   // id 확보 + UNIQUE(trigger_time) 발화
        } catch (DataIntegrityViolationException race) {
            // 동시 삽입 레이스 — trigger_time UNIQUE가 최종 방어선 (api-spec §5.1).
            // 실패한 트랜잭션은 rollback-only라 여기서 재조회하면 안 됨 → 별도 트랜잭션(REQUIRES_NEW)으로 확인.
            // 그 행이 실제로 있을 때만 409, 아니면 원인 감추지 말고 원 예외 재전파(다른 제약 위반 오분류 방지).
            Long existing = dedup.findExistingId(triggerTime).orElse(null);
            if (existing == null) {
                throw race;
            }
            throw new DuplicateTriggerException(existing);
        } catch (DataAccessException dbErr) {
            // Case 2 — DB 저장 실패(중복 외). DB에 상태를 못 남기므로 로그+재throw(→500, FastAPI 재시도).
            log.error("report save failed (DB): triggerTime={}", triggerTime, dbErr);
            throw dbErr;
        }

        saveSignals(req.logs(), report, Log::new, logs);
        saveSignals(req.metrics(), report, Metric::new, metrics);
        saveSignals(req.traces(), report, Trace::new, traces);

        // Case 1 — 에이전트 분석 실패 리포트. 저장은 하되 로그로 사유를 남긴다(reason은 DB 미저장).
        if ("FAILED".equals(req.status())) {
            log.warn("report stored as FAILED: reportId={}, triggerTime={}, reason={}",
                    report.getId(), triggerTime, req.reason());
        }

        return report.getId();
    }

    // 순수 검증 — DB 접근 전에 던진다. 유닛 테스트가 직접 호출(package-private).
    void validate(IngestRequest req) {
        if (req == null) {
            throw new InvalidPayloadException("request body is required");
        }
        // MVP 저장 상태는 DONE(분석 완료) 또는 FAILED(에이전트 분석 실패). 그 외는 유령 리포트라 거부.
        if (!"DONE".equals(req.status()) && !"FAILED".equals(req.status())) {
            throw new InvalidPayloadException("status must be DONE or FAILED");
        }
        if (req.triggerInfo() == null || req.triggerInfo().get("triggerTime") == null) {
            throw new InvalidPayloadException("triggerInfo.triggerTime is required");
        }
        // result는 DONE일 때만 필수 — FAILED는 분석 결과가 없으므로 면제.
        if ("DONE".equals(req.status()) && req.result() == null) {
            throw new InvalidPayloadException("result is required when status is DONE");
        }
    }

    private <T extends SignalRow> void saveSignals(List<SignalItem> items, Report report,
                                                   Supplier<T> factory, JpaRepository<T, Long> repo) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<T> rows = new ArrayList<>(items.size());
        for (SignalItem it : items) {
            if (it == null) {
                throw new InvalidPayloadException("signal 항목이 null입니다");
            }
            if (it.raw() == null) {                                    // raw NOT NULL (schema) → 사전 422
                throw new InvalidPayloadException("signal raw is required");
            }
            if (it.service() != null && it.service().length() > SERVICE_MAX) {  // VARCHAR(128) 초과 → 사전 422
                throw new InvalidPayloadException("service는 " + SERVICE_MAX + "자 이하여야 합니다");
            }
            T row = factory.get();
            row.setReport(report);
            row.setTs(Timestamps.parseUtc(it.timestamp()));            // null/형식오류 → InvalidPayloadException(422)
            row.setService(it.service() == null ? "" : it.service());  // 서비스 죽으면 빈 string (api-spec §2)
            row.setRaw(it.raw());
            rows.add(row);
        }
        repo.saveAll(rows);
    }
}
