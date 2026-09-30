package com.stockflow.inventory;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.inventory.dto.StockInRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng nhập kho qua JWT, tính nguyên tử, phân quyền và bất biến của sổ cái. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InventoryIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired JwtTokenProvider jwt;
 @Autowired UserRepository users;
 @Autowired RoleRepository roles;
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired InventoryRepository inventories;
 @Autowired InventoryService service;
 @Autowired JdbcTemplate jdbc;
 Product product;
 Warehouse warehouse;
 User staff;
 User admin;
 /** Mỗi test dùng dữ liệu riêng và phân công kho rõ ràng, không xóa ledger để dọn test. */
 @BeforeEach void setup() {
  String key = UUID.randomUUID().toString();
  Category category = categories.save(new Category("Danh mục " + key, key));
  product = products.save(new Product(category, key, "Sản phẩm kiểm thử", BigDecimal.TEN, ProductStatus.ACTIVE));
  warehouse = warehouses.save(new Warehouse(key, "Kho kiểm thử", "Địa chỉ kiểm thử", WarehouseStatus.ACTIVE));
  staff = user("WAREHOUSE_STAFF"); admin = user("ADMIN");
  jdbc.update("insert into warehouse_staff_assignments(user_id, warehouse_id) values (?, ?)", staff.getId(), warehouse.getId());
 }
 /** Nhân viên nhập hàng lần đầu phải tạo đúng một biến động với actor và số tồn chính xác. */
 @Test void staffStockInCreatesOneMovement() throws Exception {
  stockIn(staff, 12).andExpect(status().isCreated())
   .andExpect(jsonPath("$.available_quantity").value(12))
   .andExpect(jsonPath("$.reserved_quantity").value(0))
   .andExpect(jsonPath("$.physical_quantity").value(12));
  Long id = inventoryId();
  Map<String,Object> movement = jdbc.queryForMap("select * from inventory_movements where inventory_id = ?", id);
  assertThat(((Number)movement.get("performed_by")).longValue()).isEqualTo(staff.getId());
  assertThat(movement.get("type")).isEqualTo("GOODS_RECEIPT");
  assertThat(movement.get("quantity")).isEqualTo(12);
  assertThat(movement.get("balance_before")).isEqualTo(0);
  assertThat(movement.get("balance_after")).isEqualTo(12);
  assertThat(movement.get("note")).isEqualTo("Nhập hàng kiểm thử");
 }
 /** Giá trị âm và bằng không phải bị chặn trước khi phát sinh dữ liệu tồn kho. */
 @ParameterizedTest @ValueSource(ints = {-1, 0})
 void invalidQuantityRejected(int quantity) throws Exception {
  stockIn(staff, quantity).andExpect(status().isBadRequest());
  assertThat(inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())).isEmpty();
 }
 /** Khách hàng không có quyền nhập kho dù gửi payload hợp lệ. */
 @Test void customerDenied() throws Exception {
  stockIn(user("CUSTOMER"), 5).andExpect(status().isForbidden());
  assertThat(inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())).isEmpty();
 }
 /** Nhân viên ngoài phạm vi phân công bị từ chối; ADMIN không cần phân công. */
 @Test void unassignedStaffDeniedAndAdminAllowed() throws Exception {
  stockIn(user("WAREHOUSE_STAFF"), 5).andExpect(status().isForbidden());
  stockIn(admin, 5).andExpect(status().isCreated());
 }
 /** Lần nhập tiếp theo tái sử dụng inventory và lấy tổng tồn vật lý gồm cả hàng đã giữ. */
 @Test void subsequentReceiptUsesPhysicalBalance() throws Exception {
  stockIn(staff, 10).andExpect(status().isCreated());
  Long id = inventoryId();
  // Mô phỏng hàng đã giữ để xác minh snapshot vật lý không chỉ dùng available_quantity.
  jdbc.update("update inventories set available_quantity = 7, reserved_quantity = 3, version = version + 1 where id = ?", id);
  stockIn(staff, 5).andExpect(status().isCreated())
   .andExpect(jsonPath("$.available_quantity").value(12))
   .andExpect(jsonPath("$.physical_quantity").value(15));
  assertThat(jdbc.queryForObject("select count(*) from inventories where product_id = ? and warehouse_id = ?", Long.class, product.getId(), warehouse.getId())).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from inventory_movements where inventory_id = ?", Long.class, id)).isEqualTo(2);
  Map<String,Object> last = jdbc.queryForMap("select balance_before, balance_after from inventory_movements where inventory_id = ? order by id desc limit 1", id);
  assertThat(last.get("balance_before")).isEqualTo(10);
  assertThat(last.get("balance_after")).isEqualTo(15);
 }
 /** Database phải chặn cả SQL cập nhật và xóa ledger, không chỉ dựa vào controller. */
 @Test void ledgerCannotBeUpdatedOrDeleted() throws Exception {
  stockIn(staff, 2).andExpect(status().isCreated());
  Long id = inventoryId();
  assertThatThrownBy(() -> jdbc.update("update inventory_movements set note = 'Sửa lịch sử' where inventory_id = ?", id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(() -> jdbc.update("delete from inventory_movements where inventory_id = ?", id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(jdbc.queryForObject("select count(*) from inventory_movements where inventory_id = ?", Long.class, id)).isEqualTo(1);
 }
 /** Ràng buộc database không cho phép tạo trùng một cặp sản phẩm/kho. */
 @Test void duplicateInventoryPairRejected() throws Exception {
  stockIn(staff, 2).andExpect(status().isCreated());
  assertThatThrownBy(() -> jdbc.update("insert into inventories(product_id, warehouse_id) values (?, ?)", product.getId(), warehouse.getId()))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 /** Nếu ghi movement thất bại thì thay đổi inventory cũng phải rollback. */
 @Test void movementFailureRollsBackInventory() {
  // Gọi service trực tiếp với ghi chú vượt giới hạn để gây lỗi database sau khi inventory đã flush.
  assertThatThrownBy(() -> service.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 4, "x".repeat(256)), admin.getId()))
   .isInstanceOf(RuntimeException.class);
  assertThat(inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())).isEmpty();
 }
 /** Hai lần nhập đồng thời không tạo trùng inventory và vẫn tạo chuỗi balance liên tục. */
 @Test void concurrentReceiptsKeepConsistentLedger() throws Exception {
  ExecutorService executor = Executors.newFixedThreadPool(2);
  try {
   Callable<Void> receipt = () -> {
    service.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 5, "Nhập đồng thời"), admin.getId());
    return null;
   };
   List<Future<Void>> tasks = executor.invokeAll(List.of(receipt, receipt));
   for (Future<Void> task : tasks) task.get(15, TimeUnit.SECONDS);
   Long id = inventoryId();
   assertThat(inventories.findById(id).orElseThrow().getAvailableQuantity()).isEqualTo(10);
   List<Map<String,Object>> ledger = jdbc.queryForList("select balance_before, balance_after from inventory_movements where inventory_id = ? order by id", id);
   assertThat(ledger).hasSize(2);
   assertThat(ledger.get(0).get("balance_before")).isEqualTo(0);
   assertThat(ledger.get(0).get("balance_after")).isEqualTo(5);
   assertThat(ledger.get(1).get("balance_before")).isEqualTo(5);
   assertThat(ledger.get(1).get("balance_after")).isEqualTo(10);
  } finally { executor.shutdownNow(); }
 }
 /** Chỉ quản lý và quản trị viên xem ledger; nhân viên được đọc tồn kho của kho phụ trách. */
 @Test void readPermissionsAndFilters() throws Exception {
  stockIn(staff, 8).andExpect(status().isCreated());
  mvc.perform(get("/api/v1/inventories").param("warehouseId", warehouse.getId().toString())
    .param("productId", product.getId().toString()).header("Authorization", bearer(staff)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1));
  mvc.perform(get("/api/v1/inventories/movements").header("Authorization", bearer(staff))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/inventories/movements").param("inventoryId", inventoryId().toString())
    .param("size", "1").header("Authorization", bearer(user("MANAGER"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1))
   .andExpect(jsonPath("$.content[0].performed_by").value(staff.getId()));
  mvc.perform(get("/api/v1/inventories").header("Authorization", bearer(user("CUSTOMER")))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/inventories")).andExpect(status().isUnauthorized());
 }
 /** Tạo người dùng có role đã seed để sinh JWT thật. */
 private User user(String role) {
  return users.save(new User(UUID.randomUUID() + "@example.com", "hash-kiểm-thử", "Người kiểm thử", roles.findByName(role).orElseThrow()));
 }
 /** Tạo header xác thực từ người dùng trong database. */
 private String bearer(User user) { return "Bearer " + jwt.generateToken(user); }
 /** Gửi nhập kho qua toàn bộ filter chain và validation HTTP. */
 private org.springframework.test.web.servlet.ResultActions stockIn(User user, int quantity) throws Exception {
  Map<String,Object> payload = Map.of("product_id", product.getId(), "warehouse_id", warehouse.getId(),
   "quantity", quantity, "note", "Nhập hàng kiểm thử");
  return mvc.perform(post("/api/v1/inventories/stock-in").header("Authorization", bearer(user))
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
 }
 /** Lấy mã inventory thuộc fixture của test hiện tại. */
 private Long inventoryId() {
  return inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow().getId();
 }
}
