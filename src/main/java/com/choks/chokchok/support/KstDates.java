package com.choks.chokchok.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 날짜 필터·todayCount의 "오늘" 경계는 KST 기준(api-spec §4.1·§4.3),
 * 저장은 UTC. KST 날짜를 UTC LocalDateTime 구간으로 환산한다.
 */
public final class KstDates {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstDates() {}

    /** KST 날짜의 00:00을 UTC LocalDateTime으로. */
    public static LocalDateTime startOfDayUtc(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** UTC 반열림 구간 [from, to). */
    public record UtcRange(LocalDateTime from, LocalDateTime to) {}

    /** 오늘(KST) [start, next) 구간을 UTC로. now()를 한 번만 읽어 자정 경계에서 범위가 벌어지지 않게. */
    public static UtcRange todayRangeUtc() {
        LocalDate today = LocalDate.now(KST);
        return new UtcRange(startOfDayUtc(today), startOfDayUtc(today.plusDays(1)));
    }
}
