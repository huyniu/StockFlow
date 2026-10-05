package com.stockflow.order;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static com.stockflow.order.support.CheckoutTestData.deliveryPayload;
import static com.stockflow.order.support.CheckoutTestData.orderRequest;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.common.exception.*;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.*;
import com.stockflow.order.domain.*;
import com.stockflow.order.service.*;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng vòng đời đơn qua JWT và transaction thật, bao gồm cạnh tranh tồn kho giữa nhiều thread. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OrderIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired JwtTokenProvider jwt;
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired UserRepository users;
 @Autowired RoleRepository roles;
 @Autowired InventoryRepository inventories;
 @Autowired InventoryService inventoryService;
 @Autowired OrderService orders;
 @Autowired JdbcTemplate jdbc;
 Product product;
 Product second;
 Warehouse warehouse;
 User customer;
 User admin;
 User manager;
 /** Dữ liệu riêng cho mỗi test; không xóa lịch sử ledger để dọn fixture. */
 @BeforeEach void setup() {
  String key = UUID.randomUUID().toString();
  Category category = categories.save(new Category("Danh mục đơn " + key, key));
  product = products.save(new Product(category, key + "-1", "Sản phẩm thứ nhất", new BigDecimal("12.50"), ProductStatus.ACTIVE));
  second = products.save(new Product(category, key + "-2", "Sản phẩm thứ hai", new BigDecimal("20.00"), ProductStatus.ACTIVE));
  warehouse = warehouses.save(new Warehouse(key, "Kho đơn hàng", "Địa chỉ kiểm thử", WarehouseStatus.ACTIVE));
  customer = user("CUSTOMER"); admin = user("ADMIN"); manager = user("MANAGER");
  inventoryService.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 5, "Hàng ban đầu"), admin.getId());
  inventoryService.stockIn(new StockInRequest(second.getId(), warehouse.getId(), 5, "Hàng ban đầu"), admin.getId());
 }
 /** Đặt hàng giữ đúng 15 phút; thanh toán xuất phần đã giữ và gọi lặp chỉ ghi một payment/dispatch. */
 @Test void createAndPayThroughHttp() throws Exception {
  JsonNode order = createHttp(customer, request(2));
  Long id = order.get("id").asLong();
  assertThat(order.get("status").asText()).isEqualTo("PENDING");
  assertThat(order.get("total_amount").decimalValue()).isEqualByComparingTo("25.00");
  assertThat(Duration.between(Instant.parse(order.get("created_at").asText()),
   Instant.parse(order.get("reservation_expires_at").asText()))).isEqualTo(Duration.ofMinutes(15));
  assertStock(product, 3, 2);
  assertMovement(id, "RESERVATION_HOLD", 5, 5, customer.getId());
  mvc.perform(post("/api/v1/orders/" + id + "/payment-simulations/confirm").header("Authorization", bearer(customer)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
  mvc.perform(post("/api/v1/orders/" + id + "/payment-simulations/confirm").header("Authorization", bearer(customer)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
  assertStock(product, 3, 0);
  assertMovement(id, "DISPATCH", 5, 3, customer.getId());
  assertThat(count("select count(*) from payments where order_id = ?", id)).isEqualTo(1);
  assertThat(count("select count(*) from inventory_movements where reference_id = ? and type = 'DISPATCH'", id)).isEqualTo(1);
 }
 /** Giá được chụp lúc đặt, việc sửa catalog sau đó không đổi thanh toán của đơn cũ. */
 @Test void priceIsSnapshot() {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  jdbc.update("update products set unit_price = 99.00 where id = ?", product.getId());
  OrderResponse paid = orders.confirmPaymentSimulation(order.id(), customer.getId());
  assertThat(paid.totalAmount()).isEqualByComparingTo("25.00");
  assertThat(jdbc.queryForObject("select amount from payments where order_id = ?", BigDecimal.class, order.id())).isEqualByComparingTo("25.00");
 }
 /** Hủy đơn chờ nhả hàng một lần, gọi hủy lặp không làm tăng tồn kho lần nữa. */
 @Test void pendingCancellationReleasesOnce() {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  assertThat(orders.cancelOrder(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.CANCELLED);
  orders.cancelOrder(order.id(), customer.getId());
  assertStock(product, 5, 0);
  assertMovement(order.id(), "RESERVATION_RELEASE", 5, 5, customer.getId());
  assertThat(count("select count(*) from inventory_movements where reference_id = ? and type = 'RESERVATION_RELEASE'", order.id())).isEqualTo(1);
  assertThatThrownBy(() -> orders.confirmPaymentSimulation(order.id(), customer.getId())).isInstanceOf(ConflictException.class);
 }
 /** Khách không hủy đơn đã trả tiền; quản lý hoàn đúng hàng và chuyển payment sang REFUNDED. */
 @Test void managerCancelsPaidOrderAndRestocks() {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  orders.confirmPaymentSimulation(order.id(), customer.getId());
  assertThatThrownBy(() -> orders.cancelOrder(order.id(), customer.getId())).isInstanceOf(ForbiddenException.class);
  assertThat(orders.cancelOrder(order.id(), manager.getId()).status()).isEqualTo(OrderStatus.CANCELLED);
  orders.cancelOrder(order.id(), manager.getId());
  assertStock(product, 5, 0);
  assertMovement(order.id(), "RETURN_RESTOCK", 3, 5, manager.getId());
  assertThat(jdbc.queryForObject("select status from payments where order_id = ?", String.class, order.id())).isEqualTo("REFUNDED");
  assertThat(count("select count(*) from inventory_movements where reference_id = ? and type = 'RETURN_RESTOCK'", order.id())).isEqualTo(1);
 }
 /** Đơn đã ship bị chặn hủy, kể cả khi trạng thái vận đơn và đơn chưa đồng bộ. */
 @Test void shippedOrderCannotBeCancelled() {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  orders.confirmPaymentSimulation(order.id(), customer.getId());
  jdbc.update("insert into shipments(order_id, tracking_code, status, shipped_at) values (?, ?, 'SHIPPED', CURRENT_TIMESTAMP)", order.id(), UUID.randomUUID().toString());
  assertThatThrownBy(() -> orders.cancelOrder(order.id(), manager.getId())).isInstanceOf(ConflictException.class);
  assertStock(product, 3, 0);
  jdbc.update("update orders set status = 'SHIPPED' where id = ?", order.id());
  assertThatThrownBy(() -> orders.cancelOrder(order.id(), manager.getId())).isInstanceOf(ConflictException.class);
 }
 /** Tác vụ hết hạn nhả đúng lượng hàng, ghi actor hệ thống và không nhả hai lần khi quét lặp. */
 @Test void expiryReleasesStockAndIsRepeatable() {
  OrderResponse order = orders.createOrder(request(3), customer.getId());
  makeExpired(order.id());
  OrderExpiryScheduler scheduler = new OrderExpiryScheduler(orders);
  scheduler.expireReservations(); scheduler.expireReservations();
  assertThat(orders.getOrder(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.EXPIRED);
  assertStock(product, 5, 0);
  Long systemId = users.findByEmail("inventory-expiry@stockflow.invalid").orElseThrow().getId();
  assertMovement(order.id(), "RESERVATION_RELEASE", 5, 5, systemId);
  assertThat(count("select count(*) from inventory_movements where reference_id = ? and type = 'RESERVATION_RELEASE'", order.id())).isEqualTo(1);
 }
 /** Thanh toán sau hạn tự nhả hàng ngay, không chờ scheduler và không tạo payment. */
 @Test void latePaymentExpiresInsteadOfCharging() {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  makeExpired(order.id());
  assertThat(orders.confirmPaymentSimulation(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.EXPIRED);
  assertStock(product, 5, 0);
  assertThat(count("select count(*) from payments where order_id = ?", order.id())).isZero();
 }
 /** Thiếu mặt hàng sau khi đã giữ mặt hàng trước phải rollback cả đơn, stock và ledger. */
 @Test void insufficientSecondItemRollsBackEverything() {
  inventoryService.stockIn(new StockInRequest(second.getId(), warehouse.getId(), 1, "Bổ sung"), admin.getId());
  CreateOrderRequest request = orderRequest(warehouse.getId(), List.of(
   new CreateOrderRequest.Item(product.getId(), 2), new CreateOrderRequest.Item(second.getId(), 7)));
  assertThatThrownBy(() -> orders.createOrder(request, customer.getId())).isInstanceOf(ConflictException.class);
  assertStock(product, 5, 0); assertStock(second, 6, 0);
  assertThat(count("select count(*) from orders where customer_id = ?", customer.getId())).isZero();
  assertThat(count("select count(*) from inventory_movements where performed_by = ? and type = 'RESERVATION_HOLD'", customer.getId())).isZero();
  assertThat(count("select count(*) from order_items where order_id in (select id from orders where customer_id = ?)", customer.getId())).isZero();
 }
 /** Mặt hàng trùng bị từ chối để không giữ hàng nhiều lần hoặc vi phạm UNIQUE của order_items. */
 @Test void duplicateProductRejected() {
  CreateOrderRequest duplicate = orderRequest(warehouse.getId(), List.of(
   new CreateOrderRequest.Item(product.getId(), 1), new CreateOrderRequest.Item(product.getId(), 1)));
  assertThatThrownBy(() -> orders.createOrder(duplicate, customer.getId())).isInstanceOf(BadRequestException.class);
  assertStock(product, 5, 0);
 }
 /** Số lượng bằng không hoặc âm phải trả 400 tại API, không tạo đơn. */
 @ParameterizedTest @ValueSource(ints = {0, -1})
 void invalidQuantityRejectedByHttp(int quantity) throws Exception {
  mvc.perform(post("/api/v1/orders").header("Authorization", bearer(customer)).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(request(quantity)))).andExpect(status().isBadRequest());
 }
 /** Payload thiếu hoặc rỗng đều không được đi vào dịch vụ giữ hàng. */
 @Test void emptyItemsAndNullItemRejected() throws Exception {
  List<List<?>> invalidItems = List.of(List.of(), Collections.singletonList(null), List.of(Map.of()));
  for (List<?> items : invalidItems) {
   // Người nhận hợp lệ để lỗi 400 vẫn kiểm chứng riêng danh sách mặt hàng, không bị lỗi checkout che khuất.
   String body = json.writeValueAsString(Map.of(
     "warehouse_id", warehouse.getId(), "items", items, "delivery", deliveryPayload()));
   mvc.perform(post("/api/v1/orders").header("Authorization", bearer(customer)).contentType(MediaType.APPLICATION_JSON)
    .content(body)).andExpect(status().isBadRequest());
  }
 }
 /** Phân quyền sở hữu áp dụng cho đọc, hủy và thanh toán; staff chỉ đọc kho có phân công. */
 @Test void ownershipAndWarehouseAccess() throws Exception {
  OrderResponse order = orders.createOrder(request(1), customer.getId());
  User other = user("CUSTOMER"); User staff = user("WAREHOUSE_STAFF");
  mvc.perform(get("/api/v1/orders/" + order.id()).header("Authorization", bearer(other))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/orders/" + order.id() + "/cancel").header("Authorization", bearer(other))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/orders/" + order.id() + "/payment-simulations/confirm").header("Authorization", bearer(other))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/orders/my").header("Authorization", bearer(other))).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
  mvc.perform(get("/api/v1/orders/my").header("Authorization", bearer(customer))).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1));
  mvc.perform(get("/api/v1/orders/" + order.id()).header("Authorization", bearer(staff))).andExpect(status().isForbidden());
  jdbc.update("insert into warehouse_staff_assignments(user_id, warehouse_id) values (?, ?)", staff.getId(), warehouse.getId());
  mvc.perform(get("/api/v1/orders/" + order.id()).header("Authorization", bearer(staff))).andExpect(status().isOk());
  mvc.perform(get("/api/v1/orders/" + order.id()).header("Authorization", bearer(manager))).andExpect(status().isOk());
  mvc.perform(post("/api/v1/orders").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(request(1)))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/orders/my")).andExpect(status().isUnauthorized());
 }
 /** Sản phẩm hoặc kho ngừng hoạt động không được bán; tổng tiền phải nằm trong NUMERIC(12,2). */
 @Test void inactiveCatalogAndAmountOverflowRejected() {
  jdbc.update("update products set status = 'INACTIVE' where id = ?", product.getId());
  assertThatThrownBy(() -> orders.createOrder(request(1), customer.getId())).isInstanceOf(ConflictException.class);
  jdbc.update("update products set status = 'ACTIVE', unit_price = 9999999999.99 where id = ?", product.getId());
  assertThatThrownBy(() -> orders.createOrder(request(2), customer.getId())).isInstanceOf(BadRequestException.class);
  jdbc.update("update warehouses set status = 'INACTIVE' where id = ?", warehouse.getId());
  assertThatThrownBy(() -> orders.createOrder(request(1), customer.getId())).isInstanceOf(ConflictException.class);
 }
 /** Điểm nhấn concurrency: 24 thread tranh 5 sản phẩm, đúng 5 đơn thành công và không tồn âm. */
 @RepeatedTest(3)
 void concurrentBuyersNeverOversell() throws Exception {
  int buyers = 24;
  List<Callable<Boolean>> tasks = new ArrayList<>();
  for (int i = 0; i < buyers; i++) tasks.add(() -> {
   try { orders.createOrder(request(1), customer.getId()); return true; }
   catch (ConflictException expected) { return false; }
  });
  List<Boolean> results = runTogether(tasks);
  long succeeded = results.stream().filter(Boolean::booleanValue).count();
  assertThat(succeeded).isEqualTo(5);
  assertStock(product, 0, 5);
  assertThat(count("select count(*) from orders where customer_id = ?", customer.getId())).isEqualTo(succeeded);
  assertThat(count("select count(*) from inventory_movements where performed_by = ? and type = 'RESERVATION_HOLD'", customer.getId())).isEqualTo(succeeded);
  assertThat(jdbc.queryForObject("select min(available_quantity) from inventories where warehouse_id = ?", Integer.class, warehouse.getId())).isNotNegative();
 }
 /** Đơn nhiều mặt hàng gửi thứ tự ngược nhau vẫn kết thúc, không giữ khóa theo thứ tự client gửi. */
 @Test void oppositeItemOrderDoesNotDeadlock() throws Exception {
  CreateOrderRequest forward = orderRequest(warehouse.getId(), List.of(
   new CreateOrderRequest.Item(product.getId(), 1), new CreateOrderRequest.Item(second.getId(), 1)));
  CreateOrderRequest reverse = orderRequest(warehouse.getId(), List.of(
   new CreateOrderRequest.Item(second.getId(), 1), new CreateOrderRequest.Item(product.getId(), 1)));
  List<Callable<Boolean>> tasks = new ArrayList<>();
  for (int i = 0; i < 12; i++) {
   CreateOrderRequest input = i % 2 == 0 ? forward : reverse;
   tasks.add(() -> {
    try { orders.createOrder(input, customer.getId()); return true; }
    catch (ConflictException expected) { return false; }
   });
  }
  assertThat(runTogether(tasks).stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
  assertStock(product, 0, 5); assertStock(second, 0, 5);
 }
 /** Nhiều xác nhận cùng một đơn chỉ tạo một payment và một movement DISPATCH. */
 @Test void concurrentPaymentIsIdempotent() throws Exception {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  List<Callable<OrderStatus>> tasks = new ArrayList<>();
  for (int i = 0; i < 8; i++) tasks.add(() -> orders.confirmPaymentSimulation(order.id(), customer.getId()).status());
  assertThat(runTogether(tasks)).containsOnly(OrderStatus.CONFIRMED);
  assertStock(product, 3, 0);
  assertThat(count("select count(*) from payments where order_id = ?", order.id())).isEqualTo(1);
  assertThat(count("select count(*) from inventory_movements where reference_id = ? and type = 'DISPATCH'", order.id())).isEqualTo(1);
 }
 /** Thanh toán và quản lý hủy cùng lúc vẫn hoàn đúng tồn kho, không vừa giữ vừa xuất hai lần. */
 @Test void paymentAndCancellationRaceIsConsistent() throws Exception {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  runTogether(List.<Callable<Boolean>>of(
   () -> {
    try { orders.confirmPaymentSimulation(order.id(), customer.getId()); return true; }
    catch (ConflictException expected) { return false; }
   },
   () -> { orders.cancelOrder(order.id(), manager.getId()); return true; }));
  assertThat(orders.getOrder(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.CANCELLED);
  assertStock(product, 5, 0);
  List<String> paymentStates = jdbc.queryForList("select status from payments where order_id = ?", String.class, order.id());
  assertThat(paymentStates).allMatch("REFUNDED"::equals);
 }
 /** Hết hạn cạnh tranh với thanh toán sau deadline không thu tiền và chỉ nhả một lần. */
 @Test void expiryAndPaymentRaceReleasesOnce() throws Exception {
  OrderResponse order = orders.createOrder(request(2), customer.getId());
  makeExpired(order.id());
  runTogether(List.<Callable<Boolean>>of(
   () -> { orders.confirmPaymentSimulation(order.id(), customer.getId()); return true; },
   () -> orders.expireOrder(order.id())));
  assertThat(orders.getOrder(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.EXPIRED);
  assertStock(product, 5, 0);
  assertThat(count("select count(*) from payments where order_id = ?", order.id())).isZero();
  assertThat(count("select count(*) from inventory_movements where reference_id = ? and type = 'RESERVATION_RELEASE'", order.id())).isEqualTo(1);
 }
 /** Cho các thread xuất phát cùng lúc; Future có timeout để lỗi khóa treo làm test thất bại rõ ràng. */
 private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
  ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
  CountDownLatch ready = new CountDownLatch(tasks.size());
  CountDownLatch start = new CountDownLatch(1);
  List<Future<T>> futures = new ArrayList<>();
  try {
   for (Callable<T> task : tasks) futures.add(executor.submit(() -> {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Hết thời gian chờ bắt đầu test.");
    return task.call();
   }));
   assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
   start.countDown();
   List<T> values = new ArrayList<>();
   for (Future<T> future : futures) values.add(future.get(30, TimeUnit.SECONDS));
   return values;
  } finally { start.countDown(); executor.shutdownNow(); }
 }
 /** Gửi tạo đơn qua HTTP và trả JSON đã kiểm tra 201. */
 private JsonNode createHttp(User user, CreateOrderRequest request) throws Exception {
  return json.readTree(mvc.perform(post("/api/v1/orders").header("Authorization", bearer(user))
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
 }
 /** Tạo yêu cầu một mặt hàng tại kho fixture. */
 private CreateOrderRequest request(int quantity) {
  return orderRequest(warehouse.getId(), List.of(new CreateOrderRequest.Item(product.getId(), quantity)));
 }
 /** Tạo actor với role thật trong database để phát JWT. */
 private User user(String role) {
  return users.save(verifiedUser(UUID.randomUUID() + "@example.com", "hash-kiểm-thử", "Người kiểm thử đơn", roles.findByName(role).orElseThrow()));
 }
 /** Header xác thực qua filter chain thật. */
 private String bearer(User user) { return "Bearer " + jwt.generateToken(user); }
 /** Kiểm tra dữ liệu đã commit, không đọc số tồn từ entity cũ trong bộ nhớ. */
 private void assertStock(Product product, int available, int reserved) {
  var stock = inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
  assertThat(stock.getAvailableQuantity()).isEqualTo(available);
  assertThat(stock.getReservedQuantity()).isEqualTo(reserved);
 }
 /** Kiểm chứng một movement đúng actor và snapshot vật lý. */
 private void assertMovement(Long orderId, String type, int before, int after, Long actorId) {
  Map<String,Object> movement = jdbc.queryForMap("select balance_before, balance_after, performed_by from inventory_movements where reference_type = 'ORDER' and reference_id = ? and type = ?", orderId, type);
  assertThat(movement.get("balance_before")).isEqualTo(before);
  assertThat(movement.get("balance_after")).isEqualTo(after);
  assertThat(((Number)movement.get("performed_by")).longValue()).isEqualTo(actorId);
 }
 /** Đếm bản ghi đã commit theo điều kiện của test. */
 private long count(String sql, Long id) { return jdbc.queryForObject(sql, Long.class, id); }
 /** Đẩy deadline về quá khứ mà không cần chờ 15 phút trong integration test. */
 private void makeExpired(Long id) {
  jdbc.update("update orders set reservation_expires_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(60)), id);
 }
}
