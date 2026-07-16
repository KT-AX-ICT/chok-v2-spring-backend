package com.choks.chokchok.web;

import com.choks.chokchok.service.DuplicateTriggerException;
import com.choks.chokchok.service.InvalidPayloadException;
import com.choks.chokchok.service.ReportNotFoundException;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ReportNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", errorBody("REPORT_NOT_FOUND", e.getMessage())));
    }

    // 저장 본문(422)뿐 아니라 조회 파라미터(from/to 날짜·{id} 타입) 파싱 실패도 같은 봉투로 — 500 방지.
    @ExceptionHandler({InvalidPayloadException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, DateTimeParseException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception e) {
        String msg;
        if (e instanceof InvalidPayloadException) {
            msg = e.getMessage();
        } else if (e instanceof MethodArgumentTypeMismatchException mism) {
            msg = "잘못된 파라미터: " + mism.getName();
        } else if (e instanceof DateTimeParseException dtp) {
            msg = "잘못된 날짜 형식: " + dtp.getParsedString();
        } else {
            msg = "요청 본문을 파싱할 수 없습니다";
        }
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
