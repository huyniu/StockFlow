package com.stockflow.inventory.api;

import com.stockflow.inventory.dto.*;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.common.dto.PageResponse;
import com.stockflow.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;

/** API vận hành tồn kho và lịch sử kiểm toán; mọi tuyến đều yêu cầu JWT hợp lệ. */
@RestController @RequestMapping("/api/v1/inventories")
public class InventoryController {
 private final InventoryService service;
 /** Nhận dịch vụ thực thi nghiệp vụ nhập kho và truy vấn. */
 public InventoryController(InventoryService service) { this.service = service; }
 /** Chỉ ADMIN hoặc WAREHOUSE_STAFF được nhập hàng; mã người thực hiện luôn lấy từ JWT. */
 @PostMapping("/stock-in") @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
 public ResponseEntity<InventoryResponse> stockIn(@Valid @RequestBody StockInRequest request, @AuthenticationPrincipal User user) {
  return ResponseEntity.status(HttpStatus.CREATED).body(service.stockIn(request, user.getId()));
 }
 /** ADMIN, MANAGER và nhân viên được phân công có quyền đọc tồn kho theo sản phẩm hoặc kho. */
 @GetMapping @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
 public PageResponse<InventoryResponse> list(@RequestParam(required = false) Long productId,
   @RequestParam(required = false) Long warehouseId, @PageableDefault(size = 20, sort = "id") Pageable pageable,
   @AuthenticationPrincipal User user) {
  return PageResponse.from(service.list(productId, warehouseId, pageable, user.getId()));
 }
 /** Chỉ ADMIN và MANAGER được xem sổ cái; mặc định hiển thị biến động mới nhất. */
 @GetMapping("/movements") @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
 public PageResponse<InventoryMovementResponse> movements(@RequestParam(required = false) Long inventoryId,
   @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
  return PageResponse.from(service.history(inventoryId, pageable));
 }
}
