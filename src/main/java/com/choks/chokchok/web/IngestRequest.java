package com.choks.chokchok.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * POST /api/internal/reports 요청 바디 (api-spec §5.1).
 * 번들(§3.1: bundle_version·window·trigger_info·3종) + 분석 결과(status·severity·result).
 * trigger_info·result는 패스스루라 JsonNode 그대로 받는다.
 */
public record IngestRequest(
        @JsonProperty("bundle_version") String bundleVersion,
        Window window,
        @JsonProperty("trigger_info") JsonNode triggerInfo,
        List<SignalItem> logs,
        List<SignalItem> metrics,
        List<SignalItem> traces,
        String status,
        String severity,
        JsonNode result
) {
    public record Window(String start, String end) {}

    public record SignalItem(String timestamp, String service, String raw) {}
}
