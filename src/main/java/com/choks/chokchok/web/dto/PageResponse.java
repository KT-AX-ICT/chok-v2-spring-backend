package com.choks.chokchok.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 응답 (api-spec §4.1: content/totalElements/totalPages/page). */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page
) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber());
    }
}
