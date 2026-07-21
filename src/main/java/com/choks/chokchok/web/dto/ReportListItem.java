package com.choks.chokchok.web.dto;

/**
 * 목록 아이템 (Reports 목록 · Dashboard recentReports 공용, api-spec §2).
 * type·service는 result JSON 최상위에서 파생(Q-007 확정) — LLM 미기재 시 null 가능.
 */
public record ReportListItem(
        Long id,
        String type,
        String service,
        String severity,
        String summary,
        String detectedAt,
        String createdAt
) {}
