package com.choks.chokchok.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** KST 날짜 → UTC 구간 환산. 핵심: today 구간이 정확히 하루(24h)여야 함(자정 경계 버그 회귀). */
class KstDatesTest {

    @Test
    void start_of_day_kst_is_prev_day_15h_utc() {
        // KST 00:00 = UTC 전날 15:00
        assertEquals(LocalDateTime.of(2026, 7, 15, 15, 0, 0),
                KstDates.startOfDayUtc(LocalDate.of(2026, 7, 16)));
    }

    @Test
    void today_range_is_exactly_one_day() {
        KstDates.UtcRange r = KstDates.todayRangeUtc();
        assertTrue(r.from().isBefore(r.to()));
        // now()를 한 번만 읽으므로 항상 정확히 24시간 (자정에 걸쳐도 이틀로 안 벌어짐)
        assertEquals(r.from().plusDays(1), r.to());
    }
}
