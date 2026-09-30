package com.stockflow.order.api;
import com.stockflow.order.dto.*;
import com.stockflow.order.service.OrderService;
import com.stockflow.common.dto.PageResponse;
import com.stockflow.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;

/** API đơn hàng; actor luôn lấy từ JWT và quyền theo từng đơn được kiểm tra tại service. */
@RestController @RequestMapping("/api/v1/orders")
public class OrderController {
 private final OrderService service;
 /** Nhận dịch vụ vòng đời đơn hàng. */
 public OrderController(OrderService service) { this.service = service; }

 /** Chỉ CUSTOMER tạo đơn, server tự tính giá và giữ tồn kho. */
 @PostMapping @PreAuthorize("hasRole('CUSTOMER')")
 public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal User user) {
  return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(request, user.getId()));
 }

 /** Khách xem lịch sử của chính mình theo trang. */
 @GetMapping("/my") @PreAuthorize("hasRole('CUSTOMER')")
 public PageResponse<OrderResponse> my(@AuthenticationPrincipal User user,
   @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
  return PageResponse.from(service.myOrders(user.getId(), pageable));
 }

 /** Người vận hành xem theo role và phân công kho; khách xem theo quyền sở hữu. */
 @GetMapping("/{id}") @PreAuthorize("hasAnyRole('CUSTOMER', 'WAREHOUSE_STAFF', 'MANAGER', 'ADMIN')")
 public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
  return service.getOrder(id, user.getId());
 }

 /** Khách hủy đơn chờ của mình; MANAGER/ADMIN có thể hủy đơn đã trả tiền trước ship. */
 @PostMapping("/{id}/cancel") @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN')")
 public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal User user) {
  return service.cancelOrder(id, user.getId());
 }

 /** Khách xác nhận thanh toán mô phỏng cho đơn của mình; gọi lặp không xuất kho thêm lần nữa. */
 @PostMapping("/{id}/payment-simulations/confirm") @PreAuthorize("hasRole('CUSTOMER')")
 public OrderResponse pay(@PathVariable Long id, @AuthenticationPrincipal User user) {
  return service.confirmPaymentSimulation(id, user.getId());
 }
}
