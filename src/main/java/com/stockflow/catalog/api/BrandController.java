package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.BrandResponse;
import com.stockflow.catalog.dto.CreateBrandRequest;
import com.stockflow.catalog.dto.UpdateBrandLogoRequest;
import com.stockflow.catalog.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** API thương hiệu: khách đọc tên/logo để lọc sản phẩm, chỉ ADMIN được tạo hãng và thay logo. */
@RestController
@RequestMapping("/api/v1/brands")
@Tag(name = "Brands", description = "Thương hiệu và danh mục gợi ý cho cửa hàng")
public class BrandController {

    private final BrandService service;

    /** Nhận service; controller giữ HTTP contract và quy tắc phân quyền. */
    public BrandController(BrandService service) {
        this.service = service;
    }

    /** Công khai danh sách hãng, có thể giới hạn theo danh mục đang xem. */
    @GetMapping
    @Operation(summary = "Xem thương hiệu của cửa hàng hoặc một danh mục")
    public List<BrandResponse> listBrands(@RequestParam(required = false) Long categoryId) {
        return service.listBrands(categoryId);
    }

    /** Chỉ ADMIN được tạo hãng; staff, manager và customer bị chặn 403. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "ADMIN thêm thương hiệu và danh mục gợi ý")
    public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBrand(request));
    }

    /** Chỉ ADMIN sửa/xóa logo; các role khác không được thay nhận diện thương hiệu của cửa hàng. */
    @PatchMapping("/{id}/logo")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "ADMIN cập nhật hoặc xóa logo của thương hiệu")
    public BrandResponse updateBrandLogo(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandLogoRequest request) {
        return service.updateBrandLogo(id, request);
    }
}
