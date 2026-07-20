package com.choks.chokchok.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.choks.chokchok.web.IngestRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 저장 요청 검증 — validate()는 DB 접근 전에 던지므로 repo/dedup 없이(null) 순수 유닛.
 * status 2종(DONE/FAILED)·result 조건부 계약을 겨냥 (dedup·length는 DB 필요 → curl E2E).
 */
class IngestValidationTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final ReportIngestService svc = new ReportIngestService(null, null, null, null, null);

    private static JsonNode trigger() {
        return M.readTree("{\"triggerTime\":\"2026-07-20 12:00:00.000\"}");
    }

    private static IngestRequest req(String status, JsonNode triggerInfo, JsonNode result) {
        return new IngestRequest(null, null, triggerInfo, null, null, null, status, null, result, null);
    }

    @Test
    void null_body_rejected() {
        assertThrows(InvalidPayloadException.class, () -> svc.validate(null));
    }

    @Test
    void null_status_rejected() {
        assertThrows(InvalidPayloadException.class, () -> svc.validate(req(null, trigger(), null)));
    }

    @Test
    void invalid_status_rejected() {
        // DONE·FAILED 외 값(OPEN 등)은 조회에 안 보이는 유령 리포트라 거부
        assertThrows(InvalidPayloadException.class, () -> svc.validate(req("OPEN", trigger(), null)));
    }

    @Test
    void done_without_result_rejected() {
        // result는 DONE일 때 필수
        assertThrows(InvalidPayloadException.class, () -> svc.validate(req("DONE", trigger(), null)));
    }

    @Test
    void failed_without_result_passes() {
        // FAILED는 분석 결과가 없는 게 정상 → result null이어도 통과
        assertDoesNotThrow(() -> svc.validate(req("FAILED", trigger(), null)));
    }

    @Test
    void failed_without_triggerTime_rejected() {
        // triggerTime은 DONE·FAILED 공통 필수(멱등키+감지시각)
        assertThrows(InvalidPayloadException.class, () -> svc.validate(req("FAILED", null, null)));
    }
}
