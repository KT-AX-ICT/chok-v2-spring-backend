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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분석 완료 번들+결과를 report + 3종으로 한 트랜잭션에 저장 (SVC-01, api-spec §5.1). */
@Service
public class ReportIngestService {

    private final ReportRepository reports;
    private final LogRepository logs;
    private final MetricRepository metrics;
    private final TraceRepository traces;

    public ReportIngestService(ReportRepository reports, LogRepository logs,
                               MetricRepository metrics, TraceRepository traces) {
        this.reports = reports;
        this.logs = logs;
        this.metrics = metrics;
        this.traces = traces;
    }

    @Transactional
    public Long save(IngestRequest req) {
        validate(req);

        LocalDateTime triggerTime =
                Timestamps.parseUtc(req.triggerInfo().get("trigger_time").asString());

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
            // 실제로 그 행이 생겼을 때만 409로. 아니면 원인 감추지 말고 그대로 올림(다른 제약 위반 오분류 방지).
            // ponytail: report의 UNIQUE는 trigger_time 하나뿐이라 재조회로 판별 충분. 제약 늘면 재검토.
            Long existing = reports.findByTriggerTime(triggerTime).map(Report::getId).orElse(null);
            if (existing == null) {
                throw race;
            }
            throw new DuplicateTriggerException(existing);
        }

        saveSignals(req.logs(), report, Log::new, logs);
        saveSignals(req.metrics(), report, Metric::new, metrics);
        saveSignals(req.traces(), report, Trace::new, traces);

        return report.getId();
    }

    private void validate(IngestRequest req) {
        if (req == null) {
            throw new InvalidPayloadException("request body is required");
        }
        if (req.status() == null || req.status().isBlank()) {
            throw new InvalidPayloadException("status is required");
        }
        if (req.triggerInfo() == null || req.triggerInfo().get("trigger_time") == null) {
            throw new InvalidPayloadException("trigger_info.trigger_time is required");
        }
        // MVP는 DONE만 저장 — DONE이면 result 필수 (api-spec §5.1)
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
