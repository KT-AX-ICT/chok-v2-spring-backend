package com.choks.chokchok.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 검색어 LIKE escape — 메타문자(\ % _)가 문자 그대로 매칭되도록 escape되는지. */
class ReportSpecsTest {

    @Test
    void escapes_like_metachars() {
        assertEquals("50\\%", ReportSpecs.escapeLike("50%"));
        assertEquals("a\\_b", ReportSpecs.escapeLike("a_b"));
        // backslash를 먼저 치환해야 이후 escape가 이중처리되지 않음
        assertEquals("a\\\\b", ReportSpecs.escapeLike("a\\b"));
    }

    @Test
    void plain_text_unchanged() {
        assertEquals("cpu spike", ReportSpecs.escapeLike("cpu spike"));
    }
}
