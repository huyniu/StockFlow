package com.stockflow.order.service;

import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.common.exception.*;
import com.stockflow.inventory.domain.*;
import com.stockflow.inventory.repository.*;
import com.stockflow.order.domain.*;
import com.stockflow.order.dto.*;
import com.stockflow.order.repository.*;
import com.stockflow.user.domain.*;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Điều phối vòng đời đơn; mọi cập nhật tồn kho và movement cùng thành công hoặc cùng rollback. */
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
 /** Nhận các repository cùng EntityManager để refresh tồn kho sau UPDATE nguyên tử. */
 public OrderService(OrderRepository orders, PaymentRepository payments, ShipmentRepository shipments,
   InventoryRepository inventories, InventoryMovementRepository movements, UserRepository users,
   EntityManager em, Validator validator) {
  this.orders = orders; this.payments = payments; this.shipments = shipments;
  this.inventories = inventories; this.movements = movements; this.users = users;
  this.em = em; this.validator = validator;
 }
 /** Tạo đơn giữ hàng 15 phút; luôn cập nhật theo inventoryId tăng dần để tránh chu trình khóa chéo. */
 @Transactional
 public OrderResponse createOrder(CreateOrderRequest request, Long customerId) {
  User customer = actor(customerId);
  if (!role(customer).equals("CUSTOMER")) throw new ForbiddenException("Chỉ khách hàng được tạo đơn.");
  if (request == null || !validator.validate(request).isEmpty())
   throw new BadRequestException("Dữ liệu đặt hàng không hợp lệ.");
  Warehouse warehouse = em.find(Warehouse.class, request.warehouseId());
  if (warehouse == null) throw new ResourceNotFoundException("Không tìm thấy kho hàng.");
  if (warehouse.getStatus() != WarehouseStatus.ACTIVE) throw new ConflictException("Kho hàng đã ngừng hoạt động.");
  Set<Long> seen = new HashSet<>();
  List<StockLine> lines = new ArrayList<>();
  Order order = new Order(customerId, warehouse.getId(), Instant.now());
  for (CreateOrderRequest.Item item : request.items()) {
   if (!seen.add(item.productId())) throw new BadRequestException("Sản phẩm không được lặp trong một đơn.");
   Inventory inventory = inventories.findByProductIdAndWarehouseId(item.productId(), warehouse.getId())
    .orElseThrow(() -> new ConflictException("Sản phẩm " + item.productId() + " không đủ tồn kho tại kho đã chọn."));
   if (inventory.getProduct().getStatus() != ProductStatus.ACTIVE)
    throw new ConflictException("Sản phẩm " + item.productId() + " đã ngừng bán.");
   lines.add(new StockLine(inventory, item.quantity()));
   order.addItem(item.productId(), item.quantity(), inventory.getProduct().getUnitPrice());
  }
  if (order.getTotalAmount().compareTo(new BigDecimal("9999999999.99")) > 0)
   throw new BadRequestException("Tổng tiền đơn vượt giới hạn cho phép.");
  orders.saveAndFlush(order);
  lines.sort(Comparator.comparing(line -> line.inventory().getId()));
  for (StockLine line : lines) {
   if (inventories.reserveStock(line.inventory().getId(), line.quantity()) == 0)
    throw new ConflictException("Sản phẩm " + line.inventory().getProduct().getId() + " không đủ tồn kho.");
   record(order, line, MovementType.RESERVATION_HOLD, customerId, "Giữ hàng cho đơn mới");
  }
  return OrderResponse.from(order);
 }
 /** Khóa đơn trước thanh toán; gọi lặp trả kết quả cũ và không xuất hàng lần nữa. */
 @Transactional
 public OrderResponse confirmPaymentSimulation(Long orderId, Long customerId) {
  User customer = actor(customerId);
  Order order = locked(orderId);
  if (!role(customer).equals("CUSTOMER") || !order.getCustomerId().equals(customerId))
   throw new ForbiddenException("Bạn chỉ được thanh toán đơn của chính mình.");
  Optional<Payment> existing = payments.findByOrderId(orderId);
  if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.PAID)
   return OrderResponse.from(order);
  if (order.getStatus() == OrderStatus.EXPIRED) return OrderResponse.from(order);
  if (order.getStatus() != OrderStatus.PENDING)
   throw new ConflictException("Đơn không còn chờ thanh toán.");
  // Hết hạn được ghi nhận trong chính transaction này; không ném lỗi sau release để tránh rollback việc nhả hàng.
  if (isExpired(order, Instant.now())) {
   expireLocked(order); return OrderResponse.from(order);
  }
  for (StockLine line : sortedLines(order)) {
   if (inventories.dispatchStock(line.inventory().getId(), line.quantity()) == 0)
    throw new ConflictException("Số lượng đã giữ không đủ để xuất hàng.");
   record(order, line, MovementType.DISPATCH, customerId, "Xuất hàng sau thanh toán mô phỏng");
  }
  payments.save(new Payment(order.getId(), order.getTotalAmount()));
  order.changeStatus(OrderStatus.CONFIRMED);
  return OrderResponse.from(order);
 }
 /** Khách hủy đơn chờ của mình; quản lý hoặc ADMIN được hoàn hàng cho đơn đã xác nhận nhưng chưa ship. */
 @Transactional
 public OrderResponse cancelOrder(Long orderId, Long actorId) {
  User user = actor(actorId);
  Order order = locked(orderId);
  String role = role(user);
  boolean management = role.equals("MANAGER") || role.equals("ADMIN");
  if (!management && !(role.equals("CUSTOMER") && order.getCustomerId().equals(actorId)))
   throw new ForbiddenException("Bạn không có quyền hủy đơn này.");
  if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED)
   return OrderResponse.from(order);
  if (order.getStatus() == OrderStatus.PENDING) {
   if (isExpired(order, Instant.now())) {
    expireLocked(order); return OrderResponse.from(order);
   }
   release(order, actorId, "Hủy đơn đang giữ hàng");
  } else if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PACKED) {
   if (!management) throw new ForbiddenException("Đơn đã thanh toán chỉ quản lý hoặc quản trị viên được hủy.");
   if (shipments.findByOrderId(orderId).map(Shipment::hasShipped).orElse(false))
    throw new ConflictException("Không được hủy đơn đã giao đi.");
   Payment payment = payments.findByOrderId(orderId)
    .orElseThrow(() -> new ConflictException("Đơn đã xác nhận thiếu thanh toán."));
   if (payment.getStatus() != PaymentStatus.PAID) throw new ConflictException("Thanh toán không ở trạng thái đã trả.");
   for (StockLine line : sortedLines(order)) {
    if (inventories.restock(line.inventory().getId(), line.quantity()) == 0)
     throw new ConflictException("Không thể hoàn kho vì vượt giới hạn tồn.");
    record(order, line, MovementType.RETURN_RESTOCK, actorId, "Hoàn kho khi hủy đơn đã thanh toán");
   }
   payment.refund();
  } else throw new ConflictException("Không được hủy ở trạng thái hiện tại.");
  order.changeStatus(OrderStatus.CANCELLED);
  return OrderResponse.from(order);
 }
 /** Đọc chi tiết theo quyền: khách chỉ xem đơn mình, nhân viên chỉ xem kho được phân công. */
 @Transactional(readOnly = true)
 public OrderResponse getOrder(Long id, Long actorId) {
  User user = actor(actorId);
  Order order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
  authorizeRead(user, order);
  return OrderResponse.from(order);
 }
 /** Phân trang lịch sử cá nhân; không nhận customerId từ client. */
 @Transactional(readOnly = true)
 public Page<OrderResponse> myOrders(Long actorId, Pageable pageable) {
  User user = actor(actorId);
  if (!role(user).equals("CUSTOMER")) throw new ForbiddenException("Lịch sử cá nhân chỉ dành cho khách hàng.");
  return orders.findByCustomerId(actorId, pageable).map(OrderResponse::from);
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
  if (!isExpired(order, Instant.now())) return false;
  expireLocked(order);
  return true;
 }
 /** Nhả hàng và ghi trạng thái hết hạn với actor hệ thống riêng. */
 private void expireLocked(Order order) {
  Long systemId = users.findByEmail("inventory-expiry@stockflow.invalid")
   .orElseThrow(() -> new IllegalStateException("Thiếu tài khoản hệ thống hết hạn.")).getId();
  release(order, systemId, "Hệ thống tự động nhả hàng do hết hạn 15 phút");
  order.changeStatus(OrderStatus.EXPIRED);
 }
 /** Nhả phần giữ theo thứ tự khóa thống nhất cho mọi luồng. */
 private void release(Order order, Long actorId, String note) {
  for (StockLine line : sortedLines(order)) {
   if (inventories.releaseStock(line.inventory().getId(), line.quantity()) == 0)
    throw new ConflictException("Số lượng đã giữ không đủ để giải phóng.");
   record(order, line, MovementType.RESERVATION_RELEASE, actorId, note);
  }
 }
 /** Đọc lại số tồn sau UPDATE khi khóa dòng vẫn được giữ, tránh snapshot cũ từ persistence context. */
 private void record(Order order, StockLine line, MovementType type, Long actorId, String note) {
  em.refresh(line.inventory());
  int after = line.inventory().getPhysicalQuantity();
  int before = switch (type) {
   case DISPATCH -> Math.addExact(after, line.quantity());
   case RETURN_RESTOCK -> Math.subtractExact(after, line.quantity());
   default -> after; // Giữ hoặc nhả hàng chỉ chuyển giữa available và reserved, không đổi tồn vật lý.
  };
  movements.save(new InventoryMovement(line.inventory().getId(), actorId, type, line.quantity(),
   before, after, "ORDER", order.getId(), note));
 }
 /** Nạp các dòng tồn kho rồi sắp xếp theo inventoryId tăng dần, bất kể thứ tự mặt hàng client gửi. */
 private List<StockLine> sortedLines(Order order) {
  List<StockLine> lines = order.getItems().stream().map(item -> new StockLine(
   inventories.findByProductIdAndWarehouseId(item.getProductId(), order.getWarehouseId())
    .orElseThrow(() -> new ConflictException("Không tìm thấy tồn kho của đơn.")), item.getQuantity())).toList();
  return lines.stream().sorted(Comparator.comparing(line -> line.inventory().getId())).toList();
 }
 /** Kiểm tra hạn giữ chỗ kể cả khi scheduler chưa kịp chạy. */
 private boolean isExpired(Order order, Instant now) {
  return order.getStatus() == OrderStatus.PENDING && !order.getReservationExpiresAt().isAfter(now);
 }
 /** Khóa bản ghi đơn làm điểm tuần tự hóa vòng đời. */
 private Order locked(Long id) {
  return orders.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
 }
 /** Nạp actor từ database và chặn tài khoản không hoạt động. */
 private User actor(Long id) {
  User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng."));
  if (user.getStatus() != UserStatus.ACTIVE) throw new ForbiddenException("Tài khoản không hoạt động.");
  return user;
 }
 /** Lấy role đang có hiệu lực từ database. */
 private String role(User user) { return user.getRole().getName(); }
 /** Kiểm tra quyền sở hữu hoặc phân công kho trước khi trả dữ liệu đơn hàng. */
 private void authorizeRead(User user, Order order) {
  String role = role(user);
  if (role.equals("ADMIN") || role.equals("MANAGER")) return;
  if (role.equals("CUSTOMER") && order.getCustomerId().equals(user.getId())) return;
  if (role.equals("WAREHOUSE_STAFF")) {
   Number count = (Number) em.createNativeQuery("select count(*) from warehouse_staff_assignments where user_id = :user and warehouse_id = :warehouse")
    .setParameter("user", user.getId()).setParameter("warehouse", order.getWarehouseId()).getSingleResult();
   if (count.longValue() > 0) return;
  }
  throw new ForbiddenException("Bạn không có quyền xem đơn này.");
 }
 /** Cặp inventory và số lượng dùng nội bộ để thống nhất thứ tự cập nhật. */
 private record StockLine(Inventory inventory, int quantity) {}
}
