package com.choks.chokchok.web;

import com.choks.chokchok.service.ReportIngestService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FastAPI → Spring 내부 저장 API (WBS I8, api-spec §5.1). 인증 없음(내부 전용, D-013). */
@RestController
@RequestMapping("/api/internal/reports")
public class InternalReportController {

    private final ReportIngestService service;

    public InternalReportController(ReportIngestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody IngestRequest req) {
        Long reportId = service.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("reportId", reportId));
    }
}
