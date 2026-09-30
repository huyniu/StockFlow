package com.stockflow.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * DTO response phân trang ổn định cho API, tránh trả trực tiếp PageImpl của Spring Data ra ngoài.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages,
        boolean last) {

    /**
     * Chuyển {@link Page} của Spring Data sang DTO phân trang có cấu trúc JSON ổn định.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
