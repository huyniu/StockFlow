package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.Category;

/**
 * DTO response cho danh mục, tránh expose trực tiếp JPA entity ra API.
 */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        @JsonProperty("parent_id") Long parentId) {

    /**
     * Chuyển entity Category sang response DTO.
     */
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getSlug(),
                category.getParent() == null ? null : category.getParent().getId());
    }
}
