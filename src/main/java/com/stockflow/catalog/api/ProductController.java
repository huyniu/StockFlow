package com.stockflow.catalog.api;

import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.dto.CreateProductRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductRequest;
import com.stockflow.catalog.service.ProductService;
import com.stockflow.common.dto.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller cho sản phẩm. API đọc được public, còn tạo/sửa sản phẩm chỉ dành cho ADMIN.
 */
@RestController
@Tag(name = "Products", description = "Public product catalog with pagination and filters")
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Inject service để controller giữ phần HTTP contract và phân quyền role.
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Liệt kê sản phẩm có phân trang và filter tùy chọn theo categoryId/status.
     */
    @GetMapping
    @Operation(summary = "List products by category and status")
    public PageResponse<ProductResponse> listProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(productService.listProducts(categoryId, status, pageable));
    }

    /**
     * Xem chi tiết một sản phẩm theo id.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get product details")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    /**
     * Tạo sản phẩm mới. Chỉ ADMIN được phép thay đổi catalog vì đây là dữ liệu lõi của hệ thống.
     */
    @PostMapping
    @Operation(summary = "Create a product (ADMIN)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    /**
     * Cập nhật tên, giá hoặc trạng thái sản phẩm. Quyền này chỉ dành cho ADMIN để kiểm soát thay đổi giá bán.
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Update product name, price or status (ADMIN)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(id, request);
    }
}
