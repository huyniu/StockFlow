package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /**
     * Kiểm tra slug đã tồn tại hay chưa vì slug là định danh duy nhất dùng cho URL/tìm kiếm.
     */
    boolean existsBySlug(String slug);

    /**
     * Tìm danh mục theo id để gắn vào sản phẩm khi tạo product.
     */
    Optional<Category> findById(Long id);
}
