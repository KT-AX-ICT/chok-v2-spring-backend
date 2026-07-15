package com.choks.chokchok.web;

import com.choks.chokchok.service.DuplicateTriggerException;
import com.choks.chokchok.service.InvalidPayloadException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 전 API 공통 에러 봉투: {"error": {"code": "...", "message": "..."}} (api-spec §4). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateTriggerException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateTriggerException e) {
        Map<String, Object> err = errorBody("DUPLICATE_TRIGGER", e.getMessage());
        if (e.getReportId() != null) {
            err.put("reportId", e.getReportId());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", err));
    }

    @ExceptionHandler({InvalidPayloadException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception e) {
        String msg = (e instanceof InvalidPayloadException) ? e.getMessage() : "요청 본문을 파싱할 수 없습니다";
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", errorBody("INVALID_PAYLOAD", msg)));
    }

    private Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        return err;
    }
}
