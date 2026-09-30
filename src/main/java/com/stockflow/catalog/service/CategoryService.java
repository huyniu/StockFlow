package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.dto.CategoryResponse;
import com.stockflow.catalog.dto.CreateCategoryRequest;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.common.exception.ConflictException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý danh mục sản phẩm, bao gồm tạo danh mục và lấy danh sách danh mục.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Inject repository để service làm trung tâm xử lý nghiệp vụ catalog.
     */
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Lấy toàn bộ danh mục để client có thể hiển thị bộ lọc sản phẩm.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Tạo danh mục mới, kiểm tra trùng name và slug trước để trả lỗi 409 rõ ràng.
     */
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase();

        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("Tên danh mục đã tồn tại.");
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug danh mục đã tồn tại.");
        }

        Category category = categoryRepository.save(new Category(name, slug));
        return CategoryResponse.from(category);
    }
}
