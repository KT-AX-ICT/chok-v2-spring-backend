package com.choks.chokchok.support;

import com.choks.chokchok.service.InvalidPayloadException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 계약상 timestamp 표기가 두 갈래라 둘 다 받는다 (api-spec §6 쟁점6 미결):
 *  - ISO8601 Z:  "2026-01-15T10:00:00Z"        (window·trigger_time)
 *  - 공백형:      "2025-11-04 00:01:57.490"      (log/metric/trace 아이템, UTC 암묵)
 * 전부 UTC LocalDateTime으로 정규화 (DB DATETIME(3) 대응).
 */
public final class Timestamps {

    private static final DateTimeFormatter SPACE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");

    private static final DateTimeFormatter OUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Timestamps() {}

    /** 응답용 출력 포맷 (UTC, 초 단위). */
    public static String format(LocalDateTime t) {
        return t == null ? null : OUT.format(t);
    }

    public static LocalDateTime parseUtc(String s) {
        if (s == null || s.isBlank()) {
            throw new InvalidPayloadException("timestamp is required");
        }
        try {
            return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException iso) {
            try {
                return LocalDateTime.parse(s, SPACE);
            } catch (DateTimeParseException space) {
                throw new InvalidPayloadException("잘못된 timestamp 형식: " + s);
            }
        }
    }

    public static LocalDateTime parseUtcNullable(String s) {
        return (s == null || s.isBlank()) ? null : parseUtc(s);
    }
}
