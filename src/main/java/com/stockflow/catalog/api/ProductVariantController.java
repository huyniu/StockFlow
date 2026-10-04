package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.CreateProductVariantRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductVariantRequest;
import com.stockflow.catalog.service.ProductVariantService;
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

/** Catalog phiên bản/màu chỉ ADMIN được sửa; API đọc model vẫn công khai theo SecurityConfig. */
@RestController
@RequestMapping("/api/v1/products/{productId}/variants")
@Tag(name = "Product variants", description = "SKU màu trong từng phiên bản của cùng model")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class ProductVariantController {

    private final ProductVariantService service;

    /** Controller chỉ nhận HTTP/validation; toàn bộ thay đổi màu được service xử lý nguyên tử. */
    public ProductVariantController(ProductVariantService service) {
        this.service = service;
    }

    /** ADMIN tạo SKU màu mới trong nhóm hiện tại, role khác bị chặn 403 trước khi ghi dữ liệu. */
    @PostMapping
    @Operation(summary = "Thêm SKU màu vào phiên bản sản phẩm (ADMIN)")
    public ResponseEntity<ProductResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(productId, request));
    }

    /** ADMIN sửa giá/ảnh/trạng thái riêng của màu; không đổi SKU hoặc lịch sử tồn kho. */
    @PatchMapping("/{variantId}")
    @Operation(summary = "Sửa giá, ảnh và trạng thái màu (ADMIN)")
    public ProductResponse update(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateProductVariantRequest request) {
        return service.update(productId, variantId, request);
    }

    /** ADMIN xóa lựa chọn màu; vẫn giữ SKU và kho để không phá đơn hàng hoặc ledger cũ. */
    @DeleteMapping("/{variantId}")
    @Operation(summary = "Xóa màu khỏi cửa hàng, giữ SKU và lịch sử (ADMIN)")
    public ProductResponse archive(@PathVariable Long productId, @PathVariable Long variantId) {
        return service.archive(productId, variantId);
    }
}
