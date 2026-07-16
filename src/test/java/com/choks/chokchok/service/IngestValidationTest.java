package com.choks.chokchok.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.choks.chokchok.web.IngestRequest;
import org.junit.jupiter.api.Test;

/**
 * 저장 요청 검증 — validate()는 DB 접근 전에 던지므로 repo/dedup 없이(null) 순수 유닛 가능.
 * DONE-only 계약(v0.2.1) 강제만 겨냥 (trigger_time·result·length·dedup은 DB 필요 → curl E2E).
 */
class IngestValidationTest {

    private final ReportIngestService svc = new ReportIngestService(null, null, null, null, null);

    private static IngestRequest withStatus(String status) {
        return new IngestRequest(null, null, null, null, null, null, status, null, null);
    }

    @Test
    void null_body_rejected() {
        assertThrows(InvalidPayloadException.class, () -> svc.save(null));
    }

    @Test
    void null_status_rejected() {
        assertThrows(InvalidPayloadException.class, () -> svc.save(withStatus(null)));
    }

    @Test
    void non_done_status_rejected() {
        // "OPEN"/"ANALYZING" 등은 조회에서 안 보이는 유령 리포트가 되므로 저장 거부
        assertThrows(InvalidPayloadException.class, () -> svc.save(withStatus("OPEN")));
    }
}
