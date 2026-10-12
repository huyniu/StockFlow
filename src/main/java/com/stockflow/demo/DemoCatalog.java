package com.stockflow.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/** Dữ liệu mẫu tách khỏi logic nạp database; chỉ đọc khi bật seed-catalog. */
record DemoCatalog(List<CategorySeed> categories, List<ProductSeed> products) {

    static DemoCatalog load() {
        try (var input = new ClassPathResource("demo/catalog.json").getInputStream()) {
            return new ObjectMapper().readValue(input, DemoCatalog.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc catalog mẫu.", exception);
        }
    }

    record CategorySeed(String slug, String name) {}

    record ProductSeed(String categorySlug, String sku, String name,
            BigDecimal price, String imageUrl) {}
}
