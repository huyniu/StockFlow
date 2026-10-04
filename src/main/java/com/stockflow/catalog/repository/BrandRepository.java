package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.Brand;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository hãng; tải gợi ý theo lô và đọc cặp hãng/danh mục bằng một truy vấn tổng hợp. */
public interface BrandRepository extends JpaRepository<Brand, Long> {

    /** Tải toàn bộ hãng và danh mục gợi ý; endpoint này không phân trang sản phẩm. */
    @EntityGraph(attributePaths = "categories")
    List<Brand> findAllByOrderByIdAsc();

    /** Chặn tên trùng khác hoa/thường trước khi database kiểm tra UNIQUE. */
    boolean existsByNameIgnoreCase(String name);

    /** Slug được chuẩn hóa chữ thường tại service. */
    boolean existsBySlug(String slug);

    /** Khóa đúng hãng đang sửa logo để các lần cập nhật không ghi đè trạng thái đọc cũ. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT brand
            FROM Brand brand
            WHERE brand.id = :id
            """)
    Optional<Brand> findByIdForUpdate(@Param("id") Long id);

    /** Chỉ lấy danh mục của một hãng khi trả response cập nhật; không quét mọi sản phẩm/nhà sản xuất. */
    @Query("""
            SELECT DISTINCT product.category.id
            FROM Product product
            WHERE product.brand.id = :brandId
            """)
    List<Long> findProductCategoryIds(@Param("brandId") Long brandId);

    /** Một hãng được dùng ở danh mục khác cũng xuất hiện trong menu danh mục đó sau khi lưu sản phẩm. */
    @Query("""
            SELECT DISTINCT
                product.brand.id AS brandId,
                product.category.id AS categoryId
            FROM Product product
            WHERE product.brand IS NOT NULL
            """)
    List<ProductCategory> findProductCategories();

    /** Projection chỉ chứa ID công khai, tránh tải toàn bộ sản phẩm hoặc quan hệ tồn kho. */
    interface ProductCategory {
        /** ID hãng của sản phẩm. */
        Long getBrandId();

        /** ID danh mục của sản phẩm. */
        Long getCategoryId();
    }
}
