package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.ProductAvailabilityResponse;
import com.stockflow.catalog.service.ProductAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Khách vãng lai chỉ xem còn/hết hàng; các API tồn kho và ledger vẫn yêu cầu quyền vận hành. */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Storefront", description = "Thông tin công khai phục vụ cửa hàng bán lẻ")
public class ProductAvailabilityController {

    private final ProductAvailabilityService availability;

    /** Inject nghiệp vụ chỉ đọc, không sử dụng InventoryResponse của dashboard. */
    public ProductAvailabilityController(ProductAvailabilityService availability) {
        this.availability = availability;
    }

    /** Tồn thay đổi sau mỗi đơn; không cho browser/proxy cache tín hiệu này thành cam kết giữ hàng. */
    @GetMapping("/{id}/availability")
    @Operation(summary = "Xem tình trạng còn hàng của phiên bản/màu tại các chi nhánh đang phục vụ")
    public ResponseEntity<List<ProductAvailabilityResponse>> availability(@PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(availability.findAvailability(id));
    }
}
