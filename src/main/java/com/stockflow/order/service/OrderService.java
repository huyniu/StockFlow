package com.stockflow.order.service;

import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ForbiddenException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.domain.InventoryMovement;
import com.stockflow.inventory.domain.MovementType;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.domain.Payment;
import com.stockflow.order.domain.PaymentStatus;
import com.stockflow.order.domain.Shipment;
import com.stockflow.order.domain.ShipmentStatus;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.dto.ShipOrderRequest;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.order.repository.PaymentRepository;
import com.stockflow.order.repository.ShipmentRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Điều phối vòng đời đơn; mọi thay đổi tồn kho và movement cùng thành công hoặc cùng rollback.
 * Thanh toán xuất kho một lần; pack/ship/deliver chỉ thay đổi đơn và vận đơn, return hoàn toàn bộ hàng.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final ShipmentRepository shipments;
    private final InventoryRepository inventories;
    private final InventoryMovementRepository movements;
    private final UserRepository users;
    private final EntityManager em;
    private final Validator validator;

    /** Nhận repository và EntityManager để refresh số tồn sau UPDATE nguyên tử. */
    public OrderService(
            OrderRepository orders,
            PaymentRepository payments,
            ShipmentRepository shipments,
            InventoryRepository inventories,
            InventoryMovementRepository movements,
            UserRepository users,
            EntityManager em,
            Validator validator) {
        this.orders = orders;
        this.payments = payments;
        this.shipments = shipments;
        this.inventories = inventories;
        this.movements = movements;
        this.users = users;
        this.em = em;
        this.validator = validator;
    }

    /** Tạo đơn giữ hàng 15 phút; cập nhật theo inventoryId tăng dần để tránh chu trình khóa chéo. */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long customerId) {
        User customer = actor(customerId);
        if (!role(customer).equals("CUSTOMER")) {
            throw new ForbiddenException("Chỉ khách hàng được tạo đơn.");
        }
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BadRequestException("Dữ liệu đặt hàng không hợp lệ.");
        }
        Warehouse warehouse = em.find(Warehouse.class, request.warehouseId());
        if (warehouse == null) {
            throw new ResourceNotFoundException("Không tìm thấy kho hàng.");
        }
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new ConflictException("Kho hàng đã ngừng hoạt động.");
        }

        Set<Long> seen = new HashSet<>();
        List<StockLine> lines = new ArrayList<>();
        Order order = new Order(customerId, warehouse.getId(), Instant.now());
        for (CreateOrderRequest.Item item : request.items()) {
            if (!seen.add(item.productId())) {
                throw new BadRequestException("Sản phẩm không được lặp trong một đơn.");
            }
            Inventory inventory = inventories.findByProductIdAndWarehouseId(item.productId(), warehouse.getId())
                    .orElseThrow(() -> new ConflictException(
                            "Sản phẩm " + item.productId() + " không đủ tồn kho tại kho đã chọn."));
            if (inventory.getProduct().getStatus() != ProductStatus.ACTIVE) {
                throw new ConflictException("Sản phẩm " + item.productId() + " đã ngừng bán.");
            }
            lines.add(new StockLine(inventory, item.quantity()));
            order.addItem(item.productId(), item.quantity(), inventory.getProduct().getUnitPrice());
        }
        if (order.getTotalAmount().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new BadRequestException("Tổng tiền đơn vượt giới hạn cho phép.");
        }

        orders.saveAndFlush(order);
        lines.sort(Comparator.comparing(line -> line.inventory().getId()));
        for (StockLine line : lines) {
            if (inventories.reserveStock(line.inventory().getId(), line.quantity()) == 0) {
                throw new ConflictException(
                        "Sản phẩm " + line.inventory().getProduct().getId() + " không đủ tồn kho.");
            }
            record(order, line, MovementType.RESERVATION_HOLD, customerId, "Giữ hàng cho đơn mới");
        }
        return OrderResponse.from(order);
    }

    /** Khóa đơn trước thanh toán; gọi lặp trả kết quả cũ và không xuất hàng lần nữa. */
    @Transactional
    public OrderResponse confirmPaymentSimulation(Long orderId, Long customerId) {
        User customer = actor(customerId);
        Order order = locked(orderId);
        if (!role(customer).equals("CUSTOMER") || !order.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("Bạn chỉ được thanh toán đơn của chính mình.");
        }
        Optional<Payment> existing = payments.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.PAID) {
            return response(order);
        }
        if (order.getStatus() == OrderStatus.EXPIRED) {
            return response(order);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Đơn không còn chờ thanh toán.");
        }
        // Không ném lỗi sau release để tránh rollback việc nhả hàng của đơn vừa hết hạn.
        if (isExpired(order, Instant.now())) {
            expireLocked(order);
            return response(order);
        }
        for (StockLine line : sortedLines(order)) {
            if (inventories.dispatchStock(line.inventory().getId(), line.quantity()) == 0) {
                throw new ConflictException("Số lượng đã giữ không đủ để xuất hàng.");
            }
            record(order, line, MovementType.DISPATCH, customerId, "Xuất hàng sau thanh toán mô phỏng");
        }
        payments.save(new Payment(order.getId(), order.getTotalAmount()));
        order.changeStatus(OrderStatus.CONFIRMED);
        return response(order);
    }

    /** Khách hủy đơn chờ; MANAGER/ADMIN được hoàn hàng và tiền cho đơn CONFIRMED/PACKED chưa ship. */
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long actorId) {
        User user = actor(actorId);
        Order order = locked(orderId);
        String role = role(user);
        boolean management = role.equals("MANAGER") || role.equals("ADMIN");
        if (!management && !(role.equals("CUSTOMER") && order.getCustomerId().equals(actorId))) {
            throw new ForbiddenException("Bạn không có quyền hủy đơn này.");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            return response(order);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            if (isExpired(order, Instant.now())) {
                expireLocked(order);
                return response(order);
            }
            release(order, actorId, "Hủy đơn đang giữ hàng");
        } else if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PACKED) {
            if (!management) {
                throw new ForbiddenException("Đơn đã thanh toán chỉ quản lý hoặc quản trị viên được hủy.");
            }
            if (shipments.findByOrderId(orderId).map(Shipment::hasShipped).orElse(false)) {
                throw new ConflictException("Không được hủy đơn đã giao đi.");
            }
            Payment payment = paidPayment(order);
            restock(order, actorId, "Hoàn kho khi hủy đơn đã thanh toán");
            payment.refund();
        } else {
            throw new ConflictException("Không được hủy ở trạng thái hiện tại.");
        }
        order.changeStatus(OrderStatus.CANCELLED);
        return response(order);
    }

    /** Chỉ người vận hành có quyền trên kho được đóng gói; pack lặp không tạo thêm shipment. */
    @Transactional
    public OrderResponse packOrder(Long orderId, Long actorId) {
        User user = actor(actorId);
        Order order = locked(orderId);
        authorizeFulfillment(user, order);
        if (order.getStatus() == OrderStatus.PACKED) {
            return OrderResponse.from(order, shipmentAt(order, ShipmentStatus.PREPARING));
        }
        requireStatus(order, OrderStatus.CONFIRMED, "đóng gói");
        paidPayment(order);
        Shipment shipment = shipments.findByOrderId(orderId)
                .orElseGet(() -> new Shipment(orderId, newTrackingCode()));
        if (shipment.getStatus() != ShipmentStatus.PREPARING
                || shipment.getShippedAt() != null || shipment.getDeliveredAt() != null) {
            throw new ConflictException("Vận đơn không còn ở trạng thái chuẩn bị giao.");
        }
        shipment = saveShipment(shipment);
        order.changeStatus(OrderStatus.PACKED);
        return OrderResponse.from(order, shipment);
    }

    /** Xuất giao từ PACKED; hàng đã DISPATCH khi thanh toán nên không cập nhật inventory lần thứ hai. */
    @Transactional
    public OrderResponse shipOrder(Long orderId, ShipOrderRequest request, Long actorId) {
        User user = actor(actorId);
        Order order = locked(orderId);
        authorizeFulfillment(user, order);
        if (request != null && !validator.validate(request).isEmpty()) {
            throw new BadRequestException("Mã vận đơn không hợp lệ.");
        }
        String requestedCode = request == null ? null : request.trackingCode();
        if (order.getStatus() == OrderStatus.SHIPPED) {
            Shipment shipment = shipmentAt(order, ShipmentStatus.SHIPPED);
            if (requestedCode != null && !requestedCode.equals(shipment.getTrackingCode())) {
                throw new ConflictException("Không được đổi mã vận đơn sau khi đã xuất giao.");
            }
            return OrderResponse.from(order, shipment);
        }
        requireStatus(order, OrderStatus.PACKED, "xuất giao");
        paidPayment(order);
        Shipment shipment = shipmentAt(order, ShipmentStatus.PREPARING);
        String trackingCode = requestedCode == null ? shipment.getTrackingCode() : requestedCode;
        if (shipments.existsByTrackingCodeAndOrderIdNot(trackingCode, orderId)) {
            throw new ConflictException("Mã vận đơn đã được sử dụng cho đơn khác.");
        }
        // PostgreSQL/H2 lưu timestamp ở độ chính xác micro giây; gọi lặp giữ đúng snapshot thời gian.
        shipment.ship(trackingCode, Instant.now().truncatedTo(ChronoUnit.MICROS));
        order.changeStatus(OrderStatus.SHIPPED);
        return OrderResponse.from(order, saveShipment(shipment));
    }

    /** Ghi nhận giao thành công từ SHIPPED; gọi lặp không đổi delivered_at hoặc số tồn. */
    @Transactional
    public OrderResponse deliverOrder(Long orderId, Long actorId) {
        User user = actor(actorId);
        Order order = locked(orderId);
        authorizeFulfillment(user, order);
        if (order.getStatus() == OrderStatus.DELIVERED) {
            return OrderResponse.from(order, shipmentAt(order, ShipmentStatus.DELIVERED));
        }
        requireStatus(order, OrderStatus.SHIPPED, "giao thành công");
        paidPayment(order);
        Shipment shipment = shipmentAt(order, ShipmentStatus.SHIPPED);
        shipment.deliver(Instant.now().truncatedTo(ChronoUnit.MICROS));
        order.changeStatus(OrderStatus.DELIVERED);
        return OrderResponse.from(order, shipment);
    }

    /**
     * Nhận trả toàn bộ đơn DELIVERED, hoàn hàng theo inventoryId tăng dần và hoàn tiền mô phỏng.
     * Khóa order cùng transaction bảo đảm return lặp hoặc đồng thời chỉ tạo một lần hoàn kho.
     */
    @Transactional
    public OrderResponse returnOrder(Long orderId, Long actorId) {
        User user = actor(actorId);
        Order order = locked(orderId);
        authorizeFulfillment(user, order);
        if (order.getStatus() == OrderStatus.RETURNED) {
            return OrderResponse.from(order, shipmentAt(order, ShipmentStatus.RETURNED));
        }
        requireStatus(order, OrderStatus.DELIVERED, "nhận trả hàng");
        Payment payment = paidPayment(order);
        Shipment shipment = shipmentAt(order, ShipmentStatus.DELIVERED);
        restock(order, actorId, "Hoàn kho khi nhận trả toàn bộ đơn đã giao");
        payment.refund();
        shipment.receiveReturn();
        order.changeStatus(OrderStatus.RETURNED);
        return OrderResponse.from(order, shipment);
    }

    /** Khách chỉ xem đơn mình, nhân viên chỉ xem kho được phân công; response gồm shipment nếu có. */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id, Long actorId) {
        User user = actor(actorId);
        Order order = orders.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        authorizeRead(user, order);
        return response(order);
    }

    /** Phân trang lịch sử cá nhân; nạp shipment theo lô, không nhận customerId từ client. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> myOrders(Long actorId, Pageable pageable) {
        User user = actor(actorId);
        if (!role(user).equals("CUSTOMER")) {
            throw new ForbiddenException("Lịch sử cá nhân chỉ dành cho khách hàng.");
        }
        Page<Order> page = orders.findByCustomerId(actorId, pageable);
        List<Long> ids = page.getContent().stream().map(Order::getId).toList();
        Map<Long, Shipment> byOrder = ids.isEmpty()
                ? Map.of()
                : shipments.findByOrderIdIn(ids).stream()
                        .collect(Collectors.toMap(Shipment::getOrderId, shipment -> shipment));
        return page.map(order -> OrderResponse.from(order, byOrder.get(order.getId())));
    }

    /** Lấy tối đa 100 mã đơn hết hạn cho mỗi lần quét. */
    @Transactional(readOnly = true)
    public List<Long> expiredOrderIds() {
        return orders.findExpiredIds(OrderStatus.PENDING, Instant.now(), PageRequest.of(0, 100));
    }

    /** Mỗi đơn hết hạn có transaction riêng; kiểm tra lại sau khóa để tránh đua với thanh toán hoặc hủy. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOrder(Long id) {
        Order order = locked(id);
        if (!isExpired(order, Instant.now())) {
            return false;
        }
        expireLocked(order);
        return true;
    }

    /** Nhả hàng và ghi trạng thái hết hạn với actor hệ thống riêng. */
    private void expireLocked(Order order) {
        Long systemId = users.findByEmail("inventory-expiry@stockflow.invalid")
                .orElseThrow(() -> new IllegalStateException("Thiếu tài khoản hệ thống hết hạn."))
                .getId();
        release(order, systemId, "Hệ thống tự động nhả hàng do hết hạn 15 phút");
        order.changeStatus(OrderStatus.EXPIRED);
    }

    /** Nhả phần giữ theo thứ tự khóa thống nhất cho mọi luồng. */
    private void release(Order order, Long actorId, String note) {
        for (StockLine line : sortedLines(order)) {
            if (inventories.releaseStock(line.inventory().getId(), line.quantity()) == 0) {
                throw new ConflictException("Số lượng đã giữ không đủ để giải phóng.");
            }
            record(order, line, MovementType.RESERVATION_RELEASE, actorId, note);
        }
    }

    /** Dùng chung hoàn kho cho cancel và return; một mặt hàng lỗi làm rollback toàn bộ đơn và ledger. */
    private void restock(Order order, Long actorId, String note) {
        for (StockLine line : sortedLines(order)) {
            if (inventories.restock(line.inventory().getId(), line.quantity()) == 0) {
                throw new ConflictException("Không thể hoàn kho vì vượt giới hạn tồn.");
            }
            record(order, line, MovementType.RETURN_RESTOCK, actorId, note);
        }
    }

    /** Đọc lại số tồn sau UPDATE khi khóa dòng vẫn được giữ, tránh snapshot cũ trong persistence context. */
    private void record(Order order, StockLine line, MovementType type, Long actorId, String note) {
        em.refresh(line.inventory());
        int after = line.inventory().getPhysicalQuantity();
        int before = switch (type) {
            case DISPATCH -> Math.addExact(after, line.quantity());
            case RETURN_RESTOCK -> Math.subtractExact(after, line.quantity());
            default -> after; // Giữ/nhả chỉ chuyển giữa available và reserved, không đổi tồn vật lý.
        };
        movements.save(new InventoryMovement(
                line.inventory().getId(), actorId, type, line.quantity(), before, after,
                "ORDER", order.getId(), note));
    }

    /** Sắp xếp theo inventoryId tăng dần cho reserve, dispatch, release và restock, bất kể thứ tự client. */
    private List<StockLine> sortedLines(Order order) {
        List<StockLine> lines = order.getItems().stream().map(item -> new StockLine(
                inventories.findByProductIdAndWarehouseId(item.getProductId(), order.getWarehouseId())
                        .orElseThrow(() -> new ConflictException("Không tìm thấy tồn kho của đơn.")),
                item.getQuantity())).toList();
        return lines.stream().sorted(Comparator.comparing(line -> line.inventory().getId())).toList();
    }

    /** Kiểm tra hạn giữ chỗ kể cả khi scheduler chưa kịp chạy. */
    private boolean isExpired(Order order, Instant now) {
        return order.getStatus() == OrderStatus.PENDING && !order.getReservationExpiresAt().isAfter(now);
    }

    /** Khóa bản ghi order làm điểm tuần tự hóa cho payment/cancel/expiry và toàn bộ fulfillment. */
    private Order locked(Long id) {
        return orders.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
    }

    /** Nạp actor từ database và chặn tài khoản không hoạt động. */
    private User actor(Long id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Tài khoản không hoạt động.");
        }
        return user;
    }

    /** Lấy role đang có hiệu lực từ database. */
    private String role(User user) {
        return user.getRole().getName();
    }

    /** Kiểm tra ownership hoặc phân công kho trước khi trả dữ liệu đơn hàng. */
    private void authorizeRead(User user, Order order) {
        String role = role(user);
        if (role.equals("ADMIN") || role.equals("MANAGER")
                || (role.equals("CUSTOMER") && order.getCustomerId().equals(user.getId()))
                || (role.equals("WAREHOUSE_STAFF") && isAssigned(user, order))) {
            return;
        }
        throw new ForbiddenException("Bạn không có quyền xem đơn này.");
    }

    /** Chỉ ADMIN/MANAGER hoặc staff được phân công kho mới thao tác fulfillment; CUSTOMER luôn bị chặn. */
    private void authorizeFulfillment(User user, Order order) {
        String role = role(user);
        if (role.equals("ADMIN") || role.equals("MANAGER")
                || (role.equals("WAREHOUSE_STAFF") && isAssigned(user, order))) {
            return;
        }
        throw new ForbiddenException("Bạn không có quyền xử lý đơn tại kho này.");
    }

    /** Kiểm tra phân công từ bảng hiện có trên mỗi thao tác, không tin phạm vi kho trong token/client. */
    private boolean isAssigned(User user, Order order) {
        String sql = """
                SELECT COUNT(*)
                FROM warehouse_staff_assignments
                WHERE user_id = :user
                  AND warehouse_id = :warehouse
                """;
        Number count = (Number) em.createNativeQuery(sql)
                .setParameter("user", user.getId())
                .setParameter("warehouse", order.getWarehouseId())
                .getSingleResult();
        return count.longValue() > 0;
    }

    /** Từ chối bước sai thứ tự, không cho chuyển trạng thái lùi hoặc bỏ qua bước. */
    private void requireStatus(Order order, OrderStatus expected, String action) {
        if (order.getStatus() != expected) {
            throw new ConflictException(
                    "Đơn phải ở trạng thái " + expected + " để " + action + ".");
        }
    }

    /** Fulfillment và hoàn kho cần thanh toán PAID hợp lệ, không xử lý đơn được sửa trạng thái thủ công. */
    private Payment paidPayment(Order order) {
        Payment payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new ConflictException("Đơn đã xác nhận thiếu thanh toán."));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new ConflictException("Thanh toán không ở trạng thái đã trả.");
        }
        return payment;
    }

    /** Kiểm tra vận đơn khớp trạng thái đơn và mốc thời gian trước khi thực hiện bước tiếp theo. */
    private Shipment shipmentAt(Order order, ShipmentStatus expected) {
        Shipment shipment = shipments.findByOrderId(order.getId())
                .orElseThrow(() -> new ConflictException("Đơn thiếu vận đơn."));
        boolean hasInvalidTime = expected == ShipmentStatus.PREPARING
                ? shipment.getShippedAt() != null || shipment.getDeliveredAt() != null
                : shipment.getShippedAt() == null
                        || (expected != ShipmentStatus.SHIPPED && shipment.getDeliveredAt() == null)
                        || (expected == ShipmentStatus.SHIPPED && shipment.getDeliveredAt() != null);
        if (shipment.getStatus() != expected || hasInvalidTime) {
            throw new ConflictException("Trạng thái hoặc thời điểm vận đơn không hợp lệ.");
        }
        return shipment;
    }

    /** UNIQUE tại DB vẫn bảo vệ mã vận đơn khi hai đơn tranh cùng mã sau bước kiểm tra trước. */
    private Shipment saveShipment(Shipment shipment) {
        try {
            return shipments.saveAndFlush(shipment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Không thể lưu vận đơn do mã vận đơn hoặc dữ liệu bị trùng.");
        }
    }

    /** Tạo mã đủ ngắn cho VARCHAR(100), dùng UUID tránh trùng khi nhiều instance cùng đóng gói. */
    private String newTrackingCode() {
        return "SF-TRACK-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    /** Gắn vận đơn vào mọi response đơn hiện có, không thay contract snapshot mặt hàng. */
    private OrderResponse response(Order order) {
        return OrderResponse.from(order, shipments.findByOrderId(order.getId()).orElse(null));
    }

    /** Cặp inventory và số lượng dùng nội bộ để thống nhất thứ tự cập nhật. */
    private record StockLine(Inventory inventory, int quantity) {
    }
}
