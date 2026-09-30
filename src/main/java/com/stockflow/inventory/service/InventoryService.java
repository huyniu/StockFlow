package com.stockflow.inventory.service;

import com.stockflow.catalog.domain.Product;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.user.domain.User;
import com.stockflow.inventory.domain.*;
import com.stockflow.inventory.dto.*;
import com.stockflow.inventory.repository.*;
import com.stockflow.common.exception.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;

/** Nhập kho và ghi sổ cái nguyên tử; mỗi thay đổi tồn kho có một biến động cùng transaction. */
@Service
public class InventoryService {
 private final InventoryRepository inventories;
 private final InventoryMovementRepository movements;
 private final EntityManager entityManager;
 /** Nhận các thành phần lưu trữ để thao tác tồn kho và sổ cái trong cùng transaction. */
 public InventoryService(InventoryRepository inventories, InventoryMovementRepository movements, EntityManager entityManager) {
  this.inventories = inventories; this.movements = movements; this.entityManager = entityManager;
 }
 /** Nhập hàng, lấy người thực hiện từ phiên xác thực và kiểm tra kho được phân công. */
 @Transactional
 public InventoryResponse stockIn(StockInRequest request, Long currentUserId) {
  User actor = requireActor(currentUserId);
  String role = actor.getRole().getName();
  if (!role.equals("ADMIN") && !role.equals("WAREHOUSE_STAFF"))
   throw new ForbiddenException("Chỉ quản trị viên và nhân viên kho được nhập hàng.");
  checkWarehouse(actor, request.warehouseId());
  // Khóa kho trước khi tìm hoặc tạo inventory để tuần tự hóa nhập hàng, kể cả khi inventory chưa tồn tại.
  Warehouse warehouse = entityManager.find(Warehouse.class, request.warehouseId(), LockModeType.PESSIMISTIC_WRITE);
  if (warehouse == null) throw new ResourceNotFoundException("Không tìm thấy kho hàng.");
  Product product = entityManager.find(Product.class, request.productId());
  if (product == null) throw new ResourceNotFoundException("Không tìm thấy sản phẩm.");
  Inventory inventory = inventories.findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
   .orElseGet(() -> new Inventory(product, warehouse));
  int before = inventory.getPhysicalQuantity();
  inventory.increaseAvailable(request.quantity());
  inventories.saveAndFlush(inventory);
  movements.save(new InventoryMovement(inventory.getId(), actor.getId(), MovementType.GOODS_RECEIPT,
   request.quantity(), before, inventory.getPhysicalQuantity(), null, null, request.note()));
  return InventoryResponse.from(inventory);
 }
 /** Tra cứu có phân trang; nhân viên phải chỉ định kho được phân công. */
 @Transactional(readOnly = true)
 public Page<InventoryResponse> list(Long productId, Long warehouseId, Pageable pageable, Long actorId) {
  User actor = requireActor(actorId);
  String role = actor.getRole().getName();
  if (!role.equals("ADMIN") && !role.equals("MANAGER") && !role.equals("WAREHOUSE_STAFF"))
   throw new ForbiddenException("Bạn không có quyền xem tồn kho.");
  checkWarehouse(actor, warehouseId);
  return inventories.search(productId, warehouseId, pageable).map(InventoryResponse::from);
 }
 /** Lịch sử chỉ dành cho quản trị viên và quản lý, hỗ trợ lọc theo inventoryId. */
 @Transactional(readOnly = true)
 public Page<InventoryMovementResponse> history(Long inventoryId, Pageable pageable) {
  return movements.search(inventoryId, pageable).map(InventoryMovementResponse::from);
 }
 /** Xác minh người thực hiện còn tồn tại trước khi ghi nhận vào sổ cái. */
 private User requireActor(Long actorId) {
  User actor = entityManager.find(User.class, actorId);
  if (actor == null) throw new ResourceNotFoundException("Không tìm thấy người thực hiện.");
  return actor;
 }
 /** Nhân viên chỉ được truy cập kho có phân công; quản trị viên và quản lý được tra cứu toàn hệ thống. */
 private void checkWarehouse(User actor, Long warehouseId) {
  if (!actor.getRole().getName().equals("WAREHOUSE_STAFF")) return;
  if (warehouseId == null) throw new ForbiddenException("Nhân viên phải chỉ định kho được phân công.");
  Number count = (Number) entityManager.createNativeQuery(
   "select count(*) from warehouse_staff_assignments where user_id = :userId and warehouse_id = :warehouseId")
   .setParameter("userId", actor.getId()).setParameter("warehouseId", warehouseId).getSingleResult();
  if (count.longValue() == 0) throw new ForbiddenException("Bạn chưa được phân công cho kho này.");
 }
}
