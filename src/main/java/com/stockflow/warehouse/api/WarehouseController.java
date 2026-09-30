package com.stockflow.warehouse.api;

import com.stockflow.warehouse.dto.CreateWarehouseRequest;
import com.stockflow.warehouse.dto.WarehouseResponse;
import com.stockflow.warehouse.service.WarehouseService;
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
 * REST controller cho kho hàng. Danh sách kho chỉ mở cho các role vận hành, còn tạo kho chỉ dành cho ADMIN.
 */
@RestController
@Tag(name = "Warehouses", description = "Warehouse configuration for operational roles")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * Inject service để controller chỉ định nghĩa route, validation và phân quyền.
     */
    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /**
     * Xem danh sách kho. CUSTOMER không được xem vì kho là dữ liệu vận hành nội bộ.
     */
    @GetMapping
    @Operation(summary = "List warehouses (ADMIN, MANAGER, WAREHOUSE_STAFF)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    public List<WarehouseResponse> listWarehouses() {
        return warehouseService.listWarehouses();
    }

    /**
     * Tạo kho mới. Chỉ ADMIN được phép thêm kho để tránh thay đổi cấu trúc vận hành ngoài kiểm soát.
     */
    @PostMapping
    @Operation(summary = "Create a warehouse (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouseService.createWarehouse(request));
    }
}
