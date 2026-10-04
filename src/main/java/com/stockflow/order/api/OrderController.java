package com.stockflow.order.api;

import com.stockflow.common.dto.PageResponse;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderListResponse;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.dto.ShipOrderRequest;
import com.stockflow.order.service.OrderQueryService;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API đơn hàng; actor luôn lấy từ JWT và quyền theo từng đơn được kiểm tra tại service. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Đặt hàng, danh sách vận hành và vòng đời đơn theo quyền")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService service;
    private final OrderQueryService queries;

    /** Nhận dịch vụ vòng đời và truy vấn đơn, giữ độc lập thao tác ghi với danh sách chỉ đọc. */
    public OrderController(OrderService service, OrderQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    /** CUSTOMER tạo đơn với người nhận bắt buộc; server chụp địa chỉ, tính giá và giữ tồn kho. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Tạo đơn có thông tin nhận hàng, miễn phí giao hàng và giữ tồn 15 phút")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createOrder(request, user.getId()));
    }

    /** Danh sách vận hành dành cho quản lý và nhân viên; service áp dụng phạm vi kho trước phân trang. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Xem danh sách đơn theo trạng thái và phạm vi kho được phép")
    public PageResponse<OrderListResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(queries.listOrders(user.getId(), status, warehouseId, page, size));
    }

    /** Khách xem lịch sử của chính mình theo trang. */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Xem danh sách đơn của khách đang đăng nhập")
    public PageResponse<OrderResponse> my(
            @AuthenticationPrincipal User user,
            @ParameterObject
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.from(service.myOrders(user.getId(), pageable));
    }

    /** Người vận hành xem theo role và phân công kho; khách xem theo quyền sở hữu. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WAREHOUSE_STAFF', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Xem chi tiết đơn với kiểm tra chủ đơn và phân công kho")
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.getOrder(id, user.getId());
    }

    /** Khách hủy đơn chờ của mình; MANAGER/ADMIN có thể hủy đơn đã trả tiền trước ship. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Hủy đơn hợp lệ và trả lại tồn kho")
    public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.cancelOrder(id, user.getId());
    }

    /** Khách thanh toán mô phỏng cho đơn của mình; gọi lặp không tạo thêm payment hoặc xuất kho. */
    @PostMapping("/{id}/payment-simulations/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Xác nhận thanh toán mô phỏng có tính idempotent")
    public OrderResponse pay(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.confirmPaymentSimulation(id, user.getId());
    }

    /** ADMIN/MANAGER hoặc staff kho được giao đóng gói đơn đã thanh toán. */
    @PostMapping("/{id}/pack")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Đóng gói đơn CONFIRMED và chuẩn bị vận đơn")
    public OrderResponse pack(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.packOrder(id, user.getId());
    }

    /** Xuất giao đơn PACKED trong phạm vi kho; request/mã vận đơn tùy chọn, không xuất kho lần nữa. */
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Xuất giao đơn PACKED bằng mã vận đơn có sẵn hoặc mã được truyền")
    public OrderResponse ship(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ShipOrderRequest request,
            @AuthenticationPrincipal User user) {
        return service.shipOrder(id, request, user.getId());
    }

    /** Người vận hành đúng quyền xác nhận đơn SHIPPED đã giao thành công. */
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Xác nhận giao thành công đơn SHIPPED")
    public OrderResponse deliver(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.deliverOrder(id, user.getId());
    }

    /** Người vận hành đúng kho nhận trả toàn bộ đơn DELIVERED; hoàn kho/tiền trong một transaction. */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Nhận trả toàn bộ đơn đã giao và hoàn kho/tiền mô phỏng")
    public OrderResponse receiveReturn(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.returnOrder(id, user.getId());
    }
}
