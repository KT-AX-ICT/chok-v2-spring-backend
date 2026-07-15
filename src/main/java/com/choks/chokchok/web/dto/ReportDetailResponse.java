package com.choks.chokchok.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** 상세 봉투 {report, counts, detail} (api-spec §4.2). */
public record ReportDetailResponse(
        ReportView report,
        Counts counts,
        JsonNode detail
) {
    /** 목록 아이템 + windowStart/End + trigger_info. */
    public record ReportView(
            Long id,
            String type,
            String service,
            String severity,
            String summary,
            String detectedAt,
            String createdAt,
            String windowStart,
            String windowEnd,
            @JsonProperty("trigger_info") JsonNode triggerInfo
    ) {}

    public record Counts(long logs, long metrics, long traces) {}
}
