package com.choks.chokchok.web.dto;

import java.util.List;

/** 대시보드 요약 (api-spec §4.3). */
public record DashboardResponse(
        Summary summary,
        List<ReportListItem> recentReports
) {
    public record Summary(long total, long highCount, long todayCount) {}
}
