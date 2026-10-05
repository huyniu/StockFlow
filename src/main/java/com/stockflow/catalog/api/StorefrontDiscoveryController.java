package com.stockflow.catalog.api;

import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.service.StorefrontDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API khám phá sản phẩm cho mọi khách; chỉ trả DTO catalog, giữ dữ liệu tài chính trong API quản trị. */
@RestController
@RequestMapping("/api/v1/storefront")
@Tag(name = "Storefront", description = "Khám phá sản phẩm công khai của cửa hàng công nghệ")
public class StorefrontDiscoveryController {

    private final StorefrontDiscoveryService discovery;

    /** Nhận dịch vụ bán chạy, không nhận vai trò từ query hoặc frontend. */
    public StorefrontDiscoveryController(StorefrontDiscoveryService discovery) {
        this.discovery = discovery;
    }

    /** Bán chạy theo số lượng của các đơn đã trả tiền, gộp phiên bản/màu về một thẻ model. */
    @GetMapping("/bestsellers")
    @Operation(summary = "Xem model bán chạy từ đơn đã thanh toán, không lộ số tồn hoặc doanh thu")
    public List<ProductResponse> bestsellers(@RequestParam(defaultValue = "8") int limit) {
        return discovery.bestsellers(limit);
    }
}
