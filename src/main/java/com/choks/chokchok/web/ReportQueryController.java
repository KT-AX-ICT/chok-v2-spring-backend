package com.choks.chokchok.web;

import com.choks.chokchok.service.ReportQueryService;
import com.choks.chokchok.web.dto.DashboardResponse;
import com.choks.chokchok.web.dto.PageResponse;
import com.choks.chokchok.web.dto.ReportDetailResponse;
import com.choks.chokchok.web.dto.ReportListItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트 → Spring 조회 API (SVC-02, api-spec §4). 인증 없음(D-013). */
@RestController
@RequestMapping("/api")
public class ReportQueryController {

    private final ReportQueryService service;

    public ReportQueryController(ReportQueryService service) {
        this.service = service;
    }

    @GetMapping("/reports")
    public PageResponse<ReportListItem> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.list(severity, from, to, search, pageable));
    }

    @GetMapping("/reports/{id}")
    public ReportDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
