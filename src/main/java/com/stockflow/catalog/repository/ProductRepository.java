package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository truy vấn bảng products, hỗ trợ phân trang và filter động bằng Specification.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Kiểm tra SKU đã tồn tại hay chưa vì SKU phải là mã sản phẩm duy nhất.
     */
    boolean existsBySku(String sku);

    /**
     * Load sản phẩm kèm category để response không phát sinh lazy loading ngoài ý muốn.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    java.util.Optional<Product> findById(Long id);
}
