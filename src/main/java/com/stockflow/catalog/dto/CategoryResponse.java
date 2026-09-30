package com.stockflow.catalog.dto;

import com.stockflow.catalog.domain.Category;

/**
 * DTO response cho danh mục, tránh expose trực tiếp JPA entity ra API.
 */
public record CategoryResponse(Long id, String name, String slug) {

    /**
     * Chuyển entity Category sang response DTO.
     */
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
    }
}
