package com.choks.chokchok.service;

import com.choks.chokchok.domain.Report;
import com.choks.chokchok.repository.LogRepository;
import com.choks.chokchok.repository.MetricRepository;
import com.choks.chokchok.repository.ReportRepository;
import com.choks.chokchok.repository.TraceRepository;
import com.choks.chokchok.support.KstDates;
import com.choks.chokchok.support.Timestamps;
import com.choks.chokchok.web.dto.DashboardResponse;
import com.choks.chokchok.web.dto.ReportDetailResponse;
import com.choks.chokchok.web.dto.ReportListItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 프론트 조회 3종 — 목록·상세·대시보드 (SVC-02, api-spec §4). 전부 DONE만 노출(v0.2.1). */
@Service
public class ReportQueryService {

    private static final String DONE = "DONE";
    // 정렬 허용 필드 화이트리스트 (api-spec §4.1). detectedAt은 엔티티 triggerTime으로 매핑.
    private static final Set<String> SORT_WHITELIST = Set.of("createdAt", "severity", "detectedAt");

    private final ReportRepository reports;
    private final LogRepository logs;
    private final MetricRepository metrics;
    private final TraceRepository traces;

    public ReportQueryService(ReportRepository reports, LogRepository logs,
                              MetricRepository metrics, TraceRepository traces) {
        this.reports = reports;
        this.logs = logs;
        this.metrics = metrics;
        this.traces = traces;
    }

    @Transactional(readOnly = true)
    public Page<ReportListItem> list(String severity, String from, String to, String search, Pageable pageable) {
        Specification<Report> spec = ReportSpecs.doneOnly();
        if (hasText(severity)) {
            spec = spec.and(ReportSpecs.severity(severity));
        }
        if (hasText(from)) {
            spec = spec.and(ReportSpecs.detectedFrom(KstDates.startOfDayUtc(LocalDate.parse(from))));
        }
        if (hasText(to)) {
            spec = spec.and(ReportSpecs.detectedBefore(KstDates.startOfDayUtc(LocalDate.parse(to).plusDays(1))));
        }
        if (hasText(search)) {
            spec = spec.and(ReportSpecs.summaryLike(search));
        }
        return reports.findAll(spec, sanitizeSort(pageable)).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse detail(Long id) {
        Report r = reports.findByIdAndStatus(id, DONE).orElseThrow(() -> new ReportNotFoundException(id));
        var counts = new ReportDetailResponse.Counts(
                logs.countByReportId(id), metrics.countByReportId(id), traces.countByReportId(id));
        var view = new ReportDetailResponse.ReportView(
                r.getId(), null, null, r.getSeverity(), highlight(r.getResult()),
                Timestamps.format(r.getTriggerTime()), Timestamps.format(r.getCreatedAt()),
                Timestamps.format(r.getWindowFrom()), Timestamps.format(r.getWindowTo()),
                r.getTriggerInfo());
        return new ReportDetailResponse(view, counts, r.getResult());
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long total = reports.countByStatus(DONE);
        long high = reports.countByStatusAndSeverity(DONE, "HIGH");
        KstDates.UtcRange todayKst = KstDates.todayRangeUtc();
        long today = reports.countByStatusAndTriggerTimeGreaterThanEqualAndTriggerTimeLessThan(
                DONE, todayKst.from(), todayKst.to());
        List<ReportListItem> recent = reports.findTop5ByStatusOrderByCreatedAtDesc(DONE)
                .stream().map(this::toListItem).toList();
        return new DashboardResponse(new DashboardResponse.Summary(total, high, today), recent);
    }

    private ReportListItem toListItem(Report r) {
        // type·service는 원천 미확정(Q-007) → 현재 null. detectedAt=trigger_time.
        return new ReportListItem(r.getId(), null, null, r.getSeverity(),
                highlight(r.getResult()), Timestamps.format(r.getTriggerTime()), Timestamps.format(r.getCreatedAt()));
    }

    private static String highlight(JsonNode result) {
        if (result == null) {
            return null;
        }
        JsonNode node = result.path("summary").path("highlight");
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** 화이트리스트 밖 정렬 필드는 버리고, detectedAt→triggerTime 매핑. 비면 createdAt desc 기본. */
    private Pageable sanitizeSort(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            if (!SORT_WHITELIST.contains(o.getProperty())) {
                continue;
            }
            String prop = "detectedAt".equals(o.getProperty()) ? "triggerTime" : o.getProperty();
            orders.add(new Sort.Order(o.getDirection(), prop));
        }
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
