package com.choks.chokchok.web;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * POST /api/internal/reports 요청 바디 (api-spec §5.1).
 * 번들(§3.1: bundleVersion·window·triggerInfo·3종) + 분석 결과(status·severity·result·reason).
 * triggerInfo·result는 패스스루라 JsonNode 그대로 받는다.
 * 필드 전부 camelCase — FastAPI가 alias로 변환해 송신 (triggerInfo 내부 triggerTime·triggeredBy 포함).
 * status=FAILED(에이전트 분석 실패)면 result 생략 가능, reason에 사유 전달(로그용, DB 미저장).
 * companyCode는 사전 등록된 company의 코드 — 미등록 코드는 422 (A안, 자동 생성 없음).
 */
public record IngestRequest(
        String bundleVersion,
        String companyCode,
        Window window,
        JsonNode triggerInfo,
        List<SignalItem> logs,
        List<SignalItem> metrics,
        List<SignalItem> traces,
        String status,
        String severity,
        JsonNode result,
        String reason
) {
    public record Window(String start, String end) {}

    public record SignalItem(String timestamp, String service, String raw) {}
}
