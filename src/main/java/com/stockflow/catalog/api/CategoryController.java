package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.CategoryResponse;
import com.stockflow.catalog.dto.CreateCategoryRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller cho danh mục sản phẩm. GET được public, còn POST chỉ dành cho ADMIN.
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
    public List<CategoryResponse> listCategories() {
        return categoryService.listCategories();
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
}
