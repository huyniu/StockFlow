package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.Category;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository truy vấn bảng categories, phục vụ tạo danh mục và kiểm tra ràng buộc duy nhất của name/slug.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Tìm theo slug ổn định để seed demo chỉ tạo danh mục còn thiếu. */
    Optional<Category> findBySlug(String slug);

    /**
     * Kiểm tra tên danh mục đã tồn tại hay chưa để trả lỗi 409 thân thiện trước khi database ném unique violation.
     */
    boolean existsByName(String name);

    /** Tên khác hoa/thường cũng là cùng danh mục khi ADMIN thêm hoặc sửa. */
    boolean existsByNameIgnoreCase(String name);

    /** Cho phép giữ tên hiện tại nhưng chặn tên thuộc danh mục khác, kể cả khác hoa/thường. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Cho phép giữ slug hiện tại nhưng không chiếm đường dẫn của danh mục khác. */
    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Có danh mục con thì không được xóa cha hoặc tự động xóa cả nhánh. */
    boolean existsByParentId(Long parentId);

    /** Khóa dòng trước khi sửa/xóa; khóa ngoại tại database bảo vệ liên kết khi có request đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT category
            FROM Category category
            WHERE category.id = :id
            """)
    Optional<Category> findByIdForUpdate(@Param("id") Long id);

    /** Kiểm tra mọi SKU, kể cả đã ẩn; không xóa phân loại vẫn được sản phẩm sử dụng. */
    @Query("""
            SELECT COUNT(product) > 0
            FROM Product product
            WHERE product.category.id = :categoryId
            """)
    boolean hasProducts(@Param("categoryId") Long categoryId);

    /** Bỏ gợi ý hãng của danh mục trống trong cùng transaction, giữ nguyên hãng và sản phẩm. */
    @Modifying
    @Query(value = """
            DELETE FROM brand_categories
            WHERE category_id = :categoryId
            """, nativeQuery = true)
    int deleteBrandSuggestions(@Param("categoryId") Long categoryId);

    /** Xóa dòng sau khi kiểm tra/gỡ gợi ý; làm sạch JPA để không còn cache danh mục hoặc collection hãng cũ. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM categories
            WHERE id = :categoryId
            """, nativeQuery = true)
    int deleteCategoryRow(@Param("categoryId") Long categoryId);

    /** Tìm tên/slug không phân biệt hoa/thường; ký tự LIKE đã được escape tại service. */
    @Query("""
            SELECT category
            FROM Category category
            WHERE LOWER(category.name) LIKE :namePattern ESCAPE '!'
               OR LOWER(category.slug) LIKE :slugPattern ESCAPE '!'
            ORDER BY category.id
            """)
    List<Category> search(
            @Param("namePattern") String namePattern,
            @Param("slugPattern") String slugPattern);

    /**
     * Kiểm tra slug đã tồn tại hay chưa vì slug là định danh duy nhất dùng cho URL/tìm kiếm.
     */
    boolean existsBySlug(String slug);

    /**
     * Tìm danh mục theo id để gắn vào sản phẩm khi tạo product.
     */
    Optional<Category> findById(Long id);

    /** Đọc cây bằng một query nhỏ; không tải sản phẩm hay gọi query cho từng danh mục con. */
    @Query("""
            SELECT category.id AS id, parent.id AS parentId
            FROM Category category
            LEFT JOIN category.parent parent
            ORDER BY category.id
            """)
    List<HierarchyLink> findHierarchy();

    /** Projection chỉ chứa hai khóa để tính các nhánh của cây trong bộ nhớ. */
    interface HierarchyLink {
        /** Khóa danh mục hiện tại. */
        Long getId();

        /** Khóa cha hoặc null nếu là nhóm gốc. */
        Long getParentId();
    }
}
