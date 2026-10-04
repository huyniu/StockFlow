package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.CategoryResponse;
import com.stockflow.catalog.dto.CreateCategoryRequest;
import com.stockflow.catalog.dto.UpdateCategoryRequest;
import com.stockflow.catalog.service.CategoryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API danh mục: GET/tìm kiếm công khai, mọi thao tác thêm/sửa/xóa đều chỉ dành cho ADMIN.
 */
@RestController
@Tag(name = "Categories", description = "Public catalog categories; ADMIN manages them")
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Inject service để controller chỉ chịu trách nhiệm HTTP contract và phân quyền.
     */
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Trả về danh sách danh mục để mọi client có thể dùng làm bộ lọc catalog.
     */
    @GetMapping
    @Operation(summary = "List catalog categories")
    public List<CategoryResponse> listCategories(@RequestParam(required = false) String q) {
        return categoryService.searchCategories(q);
    }

    /**
     * Tạo danh mục mới. Chỉ ADMIN được phép thay đổi catalog để tránh user thường tự ý tạo dữ liệu lõi.
     */
    @PostMapping
    @Operation(summary = "Create a category (ADMIN)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    /** Chỉ ADMIN sửa tên/slug; ID và quan hệ cha/con vẫn giữ để không làm mất phân loại sản phẩm. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "ADMIN sửa tên và đường dẫn danh mục")
    public CategoryResponse updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    /** Chỉ ADMIN xóa nhóm trống; danh mục đang có con hoặc SKU bị chặn 409, không xóa dây chuyền. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "ADMIN xóa danh mục chưa có sản phẩm hoặc danh mục con")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
