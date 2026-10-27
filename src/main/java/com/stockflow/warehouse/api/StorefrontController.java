package com.stockflow.warehouse.api;

import com.stockflow.warehouse.dto.WarehouseOrderOptionResponse;
import com.stockflow.warehouse.service.StorefrontMapService;
import com.stockflow.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cung cấp chi nhánh phục vụ mua sắm công khai, không tiết lộ địa chỉ hoặc dữ liệu kho nội bộ. */
@RestController
@RequestMapping("/api/v1/storefront")
@Tag(name = "Storefront", description = "Thông tin công khai phục vụ cửa hàng bán lẻ")
public class StorefrontController {

    private final WarehouseService warehouses;
    private final StorefrontMapService locations;

    /** Tái sử dụng danh sách kho ACTIVE hiện có, không tạo danh sách chi nhánh hoặc ID giả ở frontend. */
    public StorefrontController(WarehouseService warehouses, StorefrontMapService locations) {
        this.warehouses = warehouses;
        this.locations = locations;
    }

    /**
     * Khách chưa đăng nhập cũng chọn được chi nhánh; tạo đơn vẫn bắt buộc JWT của CUSTOMER.
     * Kho có điểm bản đồ liên kết hiển thị tên chi nhánh (vd. "StockFlow Hà Nội") thay cho tên kho nội bộ.
     */
    @GetMapping("/branches")
    @Operation(summary = "Xem mã và tên chi nhánh đang phục vụ mua sắm")
    public List<WarehouseOrderOptionResponse> branches() {
        Map<Long, String> publicNames = locations.publicNamesByWarehouse();
        return warehouses.orderOptions().stream()
                .map(branch -> new WarehouseOrderOptionResponse(branch.id(), branch.code(),
                        publicNames.getOrDefault(branch.id(), branch.name())))
                .toList();
    }
}
