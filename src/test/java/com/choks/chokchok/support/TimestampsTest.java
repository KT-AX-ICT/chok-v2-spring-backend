package com.choks.chokchok.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.choks.chokchok.service.InvalidPayloadException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 타임스탬프 파싱/포맷 — 두 갈래 입력(ISO Z·공백형)을 UTC로 정규화하는지. */
class TimestampsTest {

    @Test
    void iso_z_is_normalized_to_utc() {
        // +09:00 → UTC 00:00 (9시간 당김)
        assertEquals(LocalDateTime.of(2026, 1, 15, 1, 0, 0),
                Timestamps.parseUtc("2026-01-15T10:00:00+09:00"));
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 0, 0),
                Timestamps.parseUtc("2026-01-15T10:00:00Z"));
    }

    @Test
    void space_form_keeps_millis() {
        assertEquals(LocalDateTime.of(2025, 11, 4, 0, 1, 57, 490_000_000),
                Timestamps.parseUtc("2025-11-04 00:01:57.490"));
    }

    @Test
    void blank_or_bad_throws_invalid_payload() {
        assertThrows(InvalidPayloadException.class, () -> Timestamps.parseUtc(null));
        assertThrows(InvalidPayloadException.class, () -> Timestamps.parseUtc(""));
        assertThrows(InvalidPayloadException.class, () -> Timestamps.parseUtc("oops"));
    }

    @Test
    void nullable_passes_null_through() {
        assertNull(Timestamps.parseUtcNullable(null));
        assertNull(Timestamps.parseUtcNullable("  "));
    }

    @Test
    void format_is_utc_seconds() {
        assertNull(Timestamps.format(null));
        assertEquals("2026-01-15 10:00:00",
                Timestamps.format(LocalDateTime.of(2026, 1, 15, 10, 0, 0, 123_000_000)));
    }
}
