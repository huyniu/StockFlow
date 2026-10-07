package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.*;
import com.stockflow.common.exception.BadRequestException;
import jakarta.persistence.criteria.*;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Match effective specifications, including version overrides, before pagination. */
final class ProductAttributeFilter {
    private ProductAttributeFilter() {}
    static Specification<Product> create(String name, String value, boolean grouped) {
        if ((name == null || name.isBlank()) && (value == null || value.isBlank())) return (r,q,b) -> b.conjunction();
        if (name == null || name.isBlank() || value == null || value.isBlank() || name.length() > 100 || value.length() > 1000)
            throw new BadRequestException("Vui lòng chọn cả tên thông số và giá trị hợp lệ.");
        String label = name.strip().toLowerCase(Locale.ROOT);
        String expected = value.strip().toLowerCase(Locale.ROOT).replace(" ", "");
        return (root, query, b) -> {
            var plain = query.subquery(Long.class);
            var product = plain.from(Product.class);
            var spec = product.join("specifications");
            plain.select(product.get("id")).where(b.equal(product.get("id"), root.get("id")),
                    b.equal(b.lower(b.trim(spec.get("name"))), label), matches(b, spec.get("value"), expected));

            var related = query.subquery(Long.class);
            var link = related.from(ProductVariant.class);
            related.select(link.get("id")).where(b.or(b.equal(link.get("product").get("id"), root.get("id")),
                    b.equal(link.get("skuProduct").get("id"), root.get("id"))));

            var variants = query.subquery(Long.class);
            var variant = variants.from(ProductVariant.class);
            var version = variant.get("version");
            var override = variants.subquery(Long.class);
            var ov = override.from(ProductVersion.class);
            var ovSpec = ov.join("specifications");
            override.select(ov.get("id")).where(b.equal(ov.get("id"), version.get("id")),
                    b.equal(b.lower(b.trim(ovSpec.get("name"))), label), matches(b, ovSpec.get("value"), expected));
            var hasOverride = variants.subquery(Long.class);
            var any = hasOverride.from(ProductVersion.class);
            var anySpec = any.join("specifications");
            hasOverride.select(any.get("id")).where(b.equal(any.get("id"), version.get("id")), b.equal(b.lower(b.trim(anySpec.get("name"))), label));
            var base = variants.subquery(Long.class);
            var parent = base.from(Product.class);
            var baseSpec = parent.join("specifications");
            base.select(parent.get("id")).where(b.equal(parent.get("id"), variant.get("product").get("id")),
                    b.equal(b.lower(b.trim(baseSpec.get("name"))), label), matches(b, baseSpec.get("value"), expected));
            variants.select(variant.get("id")).where(
                    grouped ? b.equal(variant.get("product").get("id"), root.get("id"))
                            : b.or(b.equal(variant.get("product").get("id"), root.get("id")), b.equal(variant.get("skuProduct").get("id"), root.get("id"))),
                    b.isTrue(variant.get("enabled")), b.isFalse(variant.get("archived")), b.isFalse(version.get("archived")),
                    b.equal(variant.get("skuProduct").get("status"), ProductStatus.ACTIVE),
                    b.or(b.exists(override), b.and(b.not(b.exists(hasOverride)), b.exists(base))));
            return b.or(b.exists(variants), b.and(b.not(b.exists(related)), b.exists(plain)));
        };
    }
    private static Predicate matches(CriteriaBuilder b, Expression<String> value, String expected) {
        return b.equal(b.function("replace", String.class, b.lower(b.trim(value)), b.literal(" "), b.literal("")), expected);
    }
}
