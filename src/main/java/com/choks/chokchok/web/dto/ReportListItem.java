package com.choks.chokchok.web.dto;

/**
 * 목록 아이템 (Reports 목록 · Dashboard recentReports 공용, api-spec §2).
 * type·service는 원천 미확정(Q-007)이라 현재 null 가능 — result에서 파생하거나 컬럼화는 팀 확정 후.
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
