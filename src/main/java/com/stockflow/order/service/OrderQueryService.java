package com.stockflow.order.service;

import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ForbiddenException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.OrderListResponse;
import com.stockflow.order.repository.OrderQueryRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.repository.UserRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cung cấp danh sách đơn cho vận hành; CUSTOMER tiếp tục dùng danh sách đơn của chính mình.
 * Vai trò và phạm vi kho lấy từ database, không nhận quyền hoặc mã nhân viên từ client.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private static final Set<String> OPERATIONAL_ROLES =
            Set.of("ADMIN", "MANAGER", "WAREHOUSE_STAFF");

    private final OrderQueryRepository orders;
    private final UserRepository users;

    /** Nhận repository đọc đơn và người dùng để kiểm tra quyền trong tầng nghiệp vụ. */
    public OrderQueryService(OrderQueryRepository orders, UserRepository users) {
        this.orders = orders;
        this.users = users;
    }

    /**
     * ADMIN/MANAGER xem mọi kho; nhân viên không chọn kho chỉ thấy các kho được phân công.
     * Nhân viên chưa có phân công nhận trang rỗng, không tự động được mở phạm vi toàn hệ thống.
     */
    public Page<OrderListResponse> listOrders(
            Long actorId, OrderStatus status, Long warehouseId, int page, int size) {
        User actor = users.findById(actorId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn hoạt động."));
        String role = actor.getRole().getName();
        if (!OPERATIONAL_ROLES.contains(role)) {
            throw new ForbiddenException("Bạn không có quyền xem danh sách đơn vận hành.");
        }
        validatePagination(page, size);
        if (warehouseId != null && warehouseId <= 0) {
            throw new BadRequestException("warehouseId phải lớn hơn 0.");
        }

        Long staffId = role.equals("WAREHOUSE_STAFF") ? actor.getId() : null;
        if (staffId != null && warehouseId != null && !orders.isAssigned(staffId, warehouseId)) {
            throw new ForbiddenException("Bạn không được phân công kho này.");
        }
        // Repository vẫn áp dụng EXISTS theo phân công khi đọc, kể cả sau kiểm tra kho tường minh.
        return orders.findOrders(status, warehouseId, staffId, PageRequest.of(page, size));
    }

    /** Giới hạn dữ liệu mỗi lần đọc và từ chối số trang âm thay vì âm thầm sửa tham số sai. */
    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page không được âm.");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size phải từ 1 đến 100.");
        }
    }
}
