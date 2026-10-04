package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository truy vấn bảng products, hỗ trợ phân trang và filter động bằng Specification.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Tìm SKU để khởi động lại demo không tạo thêm sản phẩm trùng hoặc đổi giá cũ. */
    Optional<Product> findBySku(String sku);

    /**
     * Kiểm tra SKU đã tồn tại hay chưa vì SKU phải là mã sản phẩm duy nhất.
     */
    boolean existsBySku(String sku);

    /**
     * Load sản phẩm kèm category để response không phát sinh lazy loading ngoài ý muốn.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);

    /** Khóa sản phẩm khi PATCH để hai ADMIN không trộn các vị trí ảnh hoặc ghi đè dữ liệu vừa cập nhật. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT product
            FROM Product product
            WHERE product.id = :id
            """)
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
