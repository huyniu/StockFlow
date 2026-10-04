package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.dto.CategoryResponse;
import com.stockflow.catalog.dto.CreateCategoryRequest;
import com.stockflow.catalog.dto.UpdateCategoryRequest;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý danh mục: tạo, tìm, sửa tên/slug và xóa nhóm trống; giữ liên kết sản phẩm/cây danh mục.
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

    /** Từ khóa rỗng giữ response cũ; hỗ trợ gõ không dấu qua slug mà không cần extension PostgreSQL. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> searchCategories(String query) {
        if (query == null || query.isBlank()) {
            return listCategories();
        }
        String keyword = query.strip().toLowerCase(Locale.ROOT);
        if (keyword.length() > 150) {
            throw new BadRequestException("Từ khóa tìm danh mục không được vượt quá 150 ký tự.");
        }
        String slugKeyword = Normalizer.normalize(keyword, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("\\s+", "-");
        return categoryRepository.search(likePattern(keyword), likePattern(slugKeyword)).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /** Escape ký tự điều khiển LIKE để dấu %, _ hoặc ! trong từ khóa không mở rộng kết quả ngoài ý muốn. */
    private String likePattern(String keyword) {
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /**
     * Tạo danh mục mới, kiểm tra trùng name và slug trước để trả lỗi 409 rõ ràng.
     */
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Tên danh mục đã tồn tại.");
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug danh mục đã tồn tại.");
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Danh mục cha không tồn tại."));
            validateParentDepth(parent);
        }
        Category category = categoryRepository.save(new Category(name, slug, parent));
        return CategoryResponse.from(category);
    }

    /** Sửa chỉ tên/slug trong transaction; không chuyển cha hoặc tạo lại ID của danh mục đang sử dụng. */
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = requireLockedCategory(id);
        String name = request.name().strip();
        String slug = request.slug().strip().toLowerCase(Locale.ROOT);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Tên danh mục đã tồn tại.");
        }
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new ConflictException("Slug danh mục đã tồn tại.");
        }
        category.updateDetails(name, slug);
        try {
            // Flush trong service để lỗi UNIQUE cạnh tranh trở thành 409 và rollback toàn bộ cập nhật.
            categoryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Tên hoặc slug vừa được danh mục khác sử dụng. Vui lòng tải lại danh sách.");
        }
        return CategoryResponse.from(category);
    }

    /** Chỉ xóa nhóm không có con/SKU; gợi ý hãng được gỡ cùng transaction và không xóa dữ liệu bán hàng. */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = requireLockedCategory(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Không thể xóa danh mục đang có danh mục con. Hãy xử lý các danh mục con trước.");
        }
        if (categoryRepository.hasProducts(id)) {
            throw new ConflictException("Không thể xóa danh mục đang có sản phẩm, kể cả sản phẩm đã ẩn.");
        }
        try {
            categoryRepository.deleteBrandSuggestions(id);
            // Bulk delete làm sạch context để collection hãng đã tải không giữ entity vừa bị xóa.
            categoryRepository.deleteCategoryRow(category.getId());
        } catch (DataIntegrityViolationException exception) {
            // Khóa ngoại là chốt bảo vệ cuối nếu sản phẩm/danh mục con vừa được tạo trong request khác.
            throw new ConflictException("Danh mục vừa được sử dụng. Vui lòng tải lại danh sách trước khi xóa.");
        }
    }

    /** Kiểm tra ID và khóa cùng dòng cho sửa/xóa; missing trả 404 thay vì ghi nhầm dữ liệu. */
    private Category requireLockedCategory(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("ID danh mục phải lớn hơn 0.");
        }
        return categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại."));
    }

    /** MVP có tối đa ba cấp; API chỉ nối danh mục mới nên không thể tạo vòng lặp với danh mục cũ. */
    private void validateParentDepth(Category parent) {
        Set<Long> visited = new HashSet<>();
        int depth = 0;
        for (Category current = parent; current != null; current = current.getParent()) {
            if (!visited.add(current.getId()) || ++depth >= 3) {
                throw new BadRequestException("Danh mục chỉ được có tối đa ba cấp và không được tạo vòng lặp.");
            }
        }
    }
}
