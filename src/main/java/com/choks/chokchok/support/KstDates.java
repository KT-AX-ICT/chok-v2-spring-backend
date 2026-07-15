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

    /** 오늘(KST)의 시작 — [today, tomorrow) 하한. */
    public static LocalDateTime todayStartUtc() {
        return startOfDayUtc(LocalDate.now(KST));
    }

    /** 오늘(KST)의 끝 — [today, tomorrow) 상한(배타). */
    public static LocalDateTime tomorrowStartUtc() {
        return startOfDayUtc(LocalDate.now(KST).plusDays(1));
    }
}
