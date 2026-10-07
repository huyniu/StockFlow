package com.stockflow.catalog.api;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductSpecificationOptionsController {
    private final JdbcTemplate jdbc;
    public ProductSpecificationOptionsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Option(String name, String value) {}
    @GetMapping("/api/v1/products/specification-options")
    public List<Option> options() {
        return jdbc.query("""
                SELECT specification_name, specification_value FROM (
                  SELECT s.specification_name, s.specification_value FROM product_specifications s
                  JOIN products p ON p.id=s.product_id WHERE p.status='ACTIVE'
                  UNION
                  SELECT s.specification_name, s.specification_value FROM product_version_specifications s
                  JOIN product_versions v ON v.id=s.version_id JOIN products p ON p.id=v.product_id
                  WHERE p.status='ACTIVE' AND v.archived=FALSE
                ) options ORDER BY specification_name, specification_value LIMIT 2000
                """, (rs, row) -> new Option(rs.getString(1), rs.getString(2)));
    }
}
