package com.choks.chokchok.service;

import com.choks.chokchok.domain.Report;
import com.choks.chokchok.domain.SignalRow;
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
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
    private final ObjectMapper mapper;

    public ReportQueryService(ReportRepository reports, LogRepository logs,
                              MetricRepository metrics, TraceRepository traces, ObjectMapper mapper) {
        this.reports = reports;
        this.logs = logs;
        this.metrics = metrics;
        this.traces = traces;
        this.mapper = mapper;
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
            spec = spec.and(ReportSpecs.searchLike(search));
        }
        return reports.findAll(spec, sanitizeSort(pageable)).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse detail(Long id) {
        Report r = reports.findByIdAndStatus(id, DONE).orElseThrow(() -> new ReportNotFoundException(id));
        var counts = new ReportDetailResponse.Counts(
                logs.countByReportId(id), metrics.countByReportId(id), traces.countByReportId(id));
        var view = new ReportDetailResponse.ReportView(
                r.getId(), field(r.getResult(), "type"), field(r.getResult(), "service"),
                r.getSeverity(), highlight(r.getResult()),
                Timestamps.format(r.getTriggerTime()), Timestamps.format(r.getCreatedAt()),
                Timestamps.format(r.getWindowFrom()), Timestamps.format(r.getWindowTo()),
                r.getTriggerInfo());
        return new ReportDetailResponse(view, counts, detailWithEvidence(r));
    }

    /**
     * detail = result 패스스루 + evidence의 lines/spans/items를 DB raw로 주입(중복 제거 분담, Q-007 후속).
     * FastAPI는 evidence에 source·conclusion만 보내고, 원본 신호는 log/metric/trace 테이블에서 채운다.
     */
    private JsonNode detailWithEvidence(Report r) {
        JsonNode result = r.getResult();
        if (result == null || !result.isObject()) {
            return result;
        }
        ObjectNode root = (ObjectNode) result.deepCopy();          // 원본 엔티티 노드 불변 유지
        ObjectNode evidence = objectChild(root, "evidence");
        fillSignals(evidence, "log", "lines", logs.findByReportIdOrderByTsAsc(r.getId()));
        fillSignals(evidence, "trace", "spans", traces.findByReportIdOrderByTsAsc(r.getId()));
        fillSignals(evidence, "metric", "items", metrics.findByReportIdOrderByTsAsc(r.getId()));
        return root;
    }

    /** evidence.<key>.<arrayField>에 각 행 raw(JSON 문자열)를 파싱해 채운다. source·conclusion은 보존. */
    private void fillSignals(ObjectNode evidence, String key, String arrayField, List<? extends SignalRow> rows) {
        ArrayNode arr = objectChild(evidence, key).putArray(arrayField);
        for (SignalRow row : rows) {
            arr.add(parseRaw(row.getRaw()));
        }
    }

    private JsonNode parseRaw(String raw) {
        try {
            return mapper.readTree(raw);                           // raw = JSON 문자열 (에이전트 계약)
        } catch (RuntimeException malformed) {
            // ponytail: 저장 데이터가 깨져도 상세 전체를 500내지 않고 원문 문자열로 흘려보냄
            return mapper.valueToTree(raw);
        }
    }

    /** parent.<field>가 객체면 그대로, 없거나 다른 타입이면 새 객체로 생성(교체). */
    private static ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode child = parent.get(field);
        return child != null && child.isObject() ? (ObjectNode) child : parent.putObject(field);
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
        // type·service는 result JSON 최상위에서 파생(Q-007 확정). detectedAt=trigger_time.
        return new ReportListItem(r.getId(), field(r.getResult(), "type"), field(r.getResult(), "service"),
                r.getSeverity(), highlight(r.getResult()),
                Timestamps.format(r.getTriggerTime()), Timestamps.format(r.getCreatedAt()));
    }

    private static String highlight(JsonNode result) {
        return result == null ? null : text(result.path("summary").path("highlight"));
    }

    /** result JSON 최상위 문자열 필드(type·service). 없거나 null이면 null. */
    private static String field(JsonNode result, String key) {
        return result == null ? null : text(result.path(key));
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** 화이트리스트 밖 정렬 필드는 버리고, detectedAt→triggerTime 매핑. 비면 createdAt desc 기본. */
    private Pageable sanitizeSort(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            if (!SORT_WHITELIST.contains(o.getProperty())) {
                continue;
            }
            if ("severity".equals(o.getProperty())) {
                // 심각도 랭크 정렬 — VARCHAR 알파벳(H<L<M) 대신 CASE로 HIGH→MID→LOW. 미지값은 맨 뒤(3).
                Sort rank = JpaSort.unsafe(o.getDirection(),
                        "(CASE WHEN severity = 'HIGH' THEN 0 WHEN severity = 'MID' THEN 1 WHEN severity = 'LOW' THEN 2 ELSE 3 END)");
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), rank);
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
