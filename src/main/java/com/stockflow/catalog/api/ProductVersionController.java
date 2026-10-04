package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.CreateProductVersionRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductVersionRequest;
import com.stockflow.catalog.service.ProductVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chỉ ADMIN sửa cấu hình; khách xem phiên bản/màu qua API chi tiết sản phẩm công khai. */
@RestController
@RequestMapping("/api/v1/products/{productId}/versions")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Product versions", description = "Các cấu hình thuộc cùng một thẻ sản phẩm")
@SecurityRequirement(name = "bearerAuth")
public class ProductVersionController {

    private final ProductVersionService service;

    /** Controller phụ trách HTTP/validation, service giữ toàn bộ thay đổi trong transaction. */
    public ProductVersionController(ProductVersionService service) {
        this.service = service;
    }

    /** ADMIN tạo cấu hình; các role khác bị chặn 403 trước khi ghi dữ liệu. */
    @PostMapping
    @Operation(summary = "Thêm phiên bản sản phẩm (ADMIN)")
    public ResponseEntity<ProductResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(productId, request));
    }

    /** ADMIN đổi tên/thông số riêng; không sửa SKU, tồn hoặc movement đã ghi. */
    @PatchMapping("/{versionId}")
    @Operation(summary = "Sửa tên và thông số phiên bản (ADMIN)")
    public ProductResponse update(
            @PathVariable Long productId,
            @PathVariable Long versionId,
            @Valid @RequestBody UpdateProductVersionRequest request) {
        return service.update(productId, versionId, request);
    }

    /** ADMIN xóa bằng lưu trữ, mọi SKU/lịch sử được giữ để giao và hoàn đơn đã tạo. */
    @DeleteMapping("/{versionId}")
    @Operation(summary = "Xóa phiên bản khỏi cửa hàng, giữ lịch sử (ADMIN)")
    public ProductResponse archive(@PathVariable Long productId, @PathVariable Long versionId) {
        return service.archive(productId, versionId);
    }

    /** ADMIN khôi phục phiên bản; không tự mở lại màu đã xóa riêng. */
    @PostMapping("/{versionId}/restore")
    @Operation(summary = "Khôi phục phiên bản đã xóa (ADMIN)")
    public ProductResponse restore(@PathVariable Long productId, @PathVariable Long versionId) {
        return service.restore(productId, versionId);
    }
}
