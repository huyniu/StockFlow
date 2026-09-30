package com.stockflow.report;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng số liệu từ fixture có nhiều trạng thái, ngày và kho.
 * Mỗi test rollback dữ liệu riêng, không xóa ledger và không phụ thuộc thứ tự chạy test khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    private User manager;
    private User customer;
    private Warehouse firstWarehouse;
    private Warehouse secondWarehouse;
    private Product firstProduct;
    private Product secondProduct;
    private Product thirdProduct;
    private final Map<OrderStatus, Baseline> baseline = new EnumMap<>(OrderStatus.class);

    /** Chụp số liệu cũ trước khi thêm fixture để order-summary vẫn kiểm tra đúng khi có dữ liệu từ test trước. */
    @BeforeEach
    void seed() {
        for (OrderStatus status : OrderStatus.values()) {
            Baseline original = jdbc.queryForObject("""
                    SELECT COUNT(*) AS total_count,
                           COALESCE(SUM(total_amount), 0) AS total_amount
                    FROM orders
                    WHERE status = :status
                    """, new MapSqlParameterSource("status", status.name()),
                    (row, index) -> new Baseline(row.getLong("total_count"), row.getBigDecimal("total_amount")));
            baseline.put(status, original);
        }
        String key = UUID.randomUUID().toString();
        Category category = categories.save(new Category("Danh mục báo cáo " + key, key));
        firstProduct = products.save(new Product(category, key + "-A", "Sản phẩm A", BigDecimal.TEN, ProductStatus.ACTIVE));
        secondProduct = products.save(new Product(category, key + "-B", "Sản phẩm B", new BigDecimal("25"), ProductStatus.ACTIVE));
        thirdProduct = products.save(new Product(category, key + "-C", "Sản phẩm C", BigDecimal.TEN, ProductStatus.ACTIVE));
        firstWarehouse = warehouses.save(new Warehouse(key + "-1", "Kho báo cáo 1", "Địa chỉ 1", WarehouseStatus.ACTIVE));
        secondWarehouse = warehouses.save(new Warehouse(key + "-2", "Kho báo cáo 2", "Địa chỉ 2", WarehouseStatus.ACTIVE));
        manager = user("MANAGER");
        customer = user("CUSTOMER");

        addOrder(OrderStatus.CONFIRMED, firstWarehouse, "2040-01-15T12:00:00Z", Map.of(firstProduct, 2));
        addOrder(OrderStatus.PACKED, firstWarehouse, "2040-01-15T23:59:59.999Z",
                Map.of(firstProduct, 1, secondProduct, 2));
        addOrder(OrderStatus.SHIPPED, firstWarehouse, "2040-01-16T00:00:00Z", Map.of(secondProduct, 1));
        addOrder(OrderStatus.DELIVERED, secondWarehouse, "2040-01-15T12:00:00Z", Map.of(firstProduct, 3));
        addOrder(OrderStatus.DELIVERED, firstWarehouse, "2040-02-01T12:00:00Z", Map.of(secondProduct, 1));
        for (OrderStatus excluded : List.of(OrderStatus.PENDING, OrderStatus.CANCELLED,
                OrderStatus.EXPIRED, OrderStatus.RETURNED)) {
            addOrder(excluded, firstWarehouse, "2040-01-15T12:00:00Z", Map.of(firstProduct, 99));
        }
        addOrder(OrderStatus.CONFIRMED, firstWarehouse, "2040-01-14T23:59:59.999Z", Map.of(thirdProduct, 1));
        addOrder(OrderStatus.CONFIRMED, firstWarehouse, "2040-01-17T00:00:00Z", Map.of(thirdProduct, 100));
        addInventory(firstProduct, firstWarehouse, 3, 7);
        addInventory(secondProduct, firstWarehouse, 10, 0);
        addInventory(thirdProduct, firstWarehouse, 11, 0);
        addInventory(firstProduct, secondWarehouse, 0, 0);
    }

    /** Doanh thu theo ngày/kho đếm đơn một lần, không nhân tổng tiền theo số dòng order_items. */
    @Test
    void revenueDailyIncludesOnlyEligibleStatuses() throws Exception {
        JsonNode report = report(get("/api/v1/reports/revenue")
                .param("fromDate", "2040-01-15")
                .param("toDate", "2040-01-16"));
        assertThat(report.get("total_elements").asLong()).isEqualTo(3);
        JsonNode first = findWarehousePeriod(report, firstWarehouse.getId(), "2040-01-15");
        assertThat(first.get("total_orders").asLong()).isEqualTo(2);
        assertThat(first.get("total_revenue").decimalValue()).isEqualByComparingTo("80");
        JsonNode next = findWarehousePeriod(report, firstWarehouse.getId(), "2040-01-16");
        assertThat(next.get("total_orders").asLong()).isEqualTo(1);
        assertThat(next.get("total_revenue").decimalValue()).isEqualByComparingTo("25");
        assertThat(findWarehousePeriod(report, secondWarehouse.getId(), "2040-01-15")
                .get("total_revenue").decimalValue()).isEqualByComparingTo("30");
    }

    /** MONTH dùng ngày đầu tháng làm period; bộ lọc ngày vẫn áp dụng trước khi nhóm. */
    @Test
    void revenueMonthlyAndWarehouseFilter() throws Exception {
        JsonNode report = report(get("/api/v1/reports/revenue")
                .param("fromDate", "2040-01-15")
                .param("toDate", "2040-02-28")
                .param("warehouseId", firstWarehouse.getId().toString())
                .param("groupBy", "MONTH")
                .param("size", "1")
                .param("page", "0"));
        assertThat(report.get("total_elements").asLong()).isEqualTo(2);
        assertThat(report.get("total_pages").asInt()).isEqualTo(2);
        JsonNode january = report.get("content").get(0);
        assertThat(january.get("period").asText()).isEqualTo("2040-01-01");
        assertThat(january.get("total_orders").asLong()).isEqualTo(4);
        assertThat(january.get("total_revenue").decimalValue()).isEqualByComparingTo("1105");
        JsonNode secondPage = report(get("/api/v1/reports/revenue")
                .param("fromDate", "2040-01-15")
                .param("toDate", "2040-02-28")
                .param("warehouseId", firstWarehouse.getId().toString())
                .param("groupBy", "MONTH")
                .param("size", "1")
                .param("page", "1"));
        assertThat(secondPage.get("content").get(0).get("period").asText()).isEqualTo("2040-02-01");
        assertThat(secondPage.get("content").get(0).get("total_revenue").decimalValue()).isEqualByComparingTo("25");
    }

    /** Tổng hợp toàn bộ toDate và xử lý UTC đúng ngay cả khi offset của timestamp khác UTC. */
    @Test
    void dateBoundariesAndUtcBucket() throws Exception {
        addOrder(OrderStatus.CONFIRMED, firstWarehouse, "2040-01-16T00:30:00+07:00", Map.of(firstProduct, 1));
        JsonNode report = report(get("/api/v1/reports/revenue")
                .param("fromDate", "2040-01-15")
                .param("toDate", "2040-01-15")
                .param("warehouseId", firstWarehouse.getId().toString()));
        assertThat(report.get("total_elements").asLong()).isEqualTo(1);
        assertThat(report.get("content").get(0).get("total_orders").asLong()).isEqualTo(3);
        assertThat(report.get("content").get(0).get("total_revenue").decimalValue()).isEqualByComparingTo("90");
    }

    /** Top dùng line_total đã chụp, xếp theo doanh thu thay vì giá hiện tại hoặc số lượng đơn thuần. */
    @Test
    void topProductsUsesSnapshotAndPagination() throws Exception {
        jdbc.update("""
                UPDATE products
                SET unit_price = 999
                WHERE id = :id
                """, new MapSqlParameterSource("id", firstProduct.getId()));
        JsonNode firstPage = report(get("/api/v1/reports/top-products")
                .param("fromDate", "2040-01-15")
                .param("toDate", "2040-01-16")
                .param("limit", "1"));
        assertThat(firstPage.get("total_elements").asLong()).isEqualTo(2);
        assertThat(firstPage.get("total_pages").asInt()).isEqualTo(2);
        JsonNode best = firstPage.get("content").get(0);
        assertThat(best.get("product_id").asLong()).isEqualTo(secondProduct.getId());
        assertThat(best.get("product_sku").asText()).isEqualTo(secondProduct.getSku());
        assertThat(best.get("total_quantity_sold").asLong()).isEqualTo(3);
        assertThat(best.get("total_revenue").decimalValue()).isEqualByComparingTo("75");
        JsonNode next = report(get("/api/v1/reports/top-products")
                .param("fromDate", "2040-01-15")
                .param("toDate", "2040-01-16")
                .param("size", "1")
                .param("page", "1")).get("content").get(0);
        assertThat(next.get("product_id").asLong()).isEqualTo(firstProduct.getId());
        assertThat(next.get("total_quantity_sold").asLong()).isEqualTo(6);
        assertThat(next.get("total_revenue").decimalValue()).isEqualByComparingTo("60");
    }

    /** Cùng doanh thu và số lượng vẫn phân trang ổn định nhờ productId làm khóa cuối. */
    @Test
    void topTiesHaveDeterministicOrder() throws Exception {
        addOrder(OrderStatus.DELIVERED, firstWarehouse, "2041-03-01T00:00:00Z",
                Map.of(firstProduct, 1, thirdProduct, 1));
        JsonNode report = report(get("/api/v1/reports/top-products")
                .param("fromDate", "2041-03-01").param("toDate", "2041-03-01"));
        assertThat(report.get("content").get(0).get("product_id").asLong()).isEqualTo(firstProduct.getId());
        assertThat(report.get("content").get(1).get("product_id").asLong()).isEqualTo(thirdProduct.getId());
    }

    /** Low-stock dùng available <= threshold, giữ đúng hàng bằng ngưỡng và phân biệt số tồn vật lý. */
    @Test
    void lowStockThresholdAndWarehousePagination() throws Exception {
        JsonNode report = report(get("/api/v1/reports/low-stock")
                .param("warehouseId", firstWarehouse.getId().toString()).param("size", "1"));
        assertThat(report.get("total_elements").asLong()).isEqualTo(2);
        JsonNode stock = report.get("content").get(0);
        assertThat(stock.get("product_id").asLong()).isEqualTo(firstProduct.getId());
        assertThat(stock.get("available_quantity").asInt()).isEqualTo(3);
        assertThat(stock.get("reserved_quantity").asInt()).isEqualTo(7);
        assertThat(stock.get("physical_quantity").asLong()).isEqualTo(10);
        JsonNode next = report(get("/api/v1/reports/low-stock")
                .param("warehouseId", firstWarehouse.getId().toString())
                .param("size", "1").param("page", "1"));
        assertThat(next.get("content").get(0).get("available_quantity").asInt()).isEqualTo(10);
        JsonNode tight = report(get("/api/v1/reports/low-stock")
                .param("warehouseId", firstWarehouse.getId().toString()).param("threshold", "3"));
        assertThat(tight.get("total_elements").asLong()).isEqualTo(1);
        JsonNode zero = report(get("/api/v1/reports/low-stock")
                .param("warehouseId", secondWarehouse.getId().toString()).param("threshold", "0"));
        assertThat(zero.get("total_elements").asLong()).isEqualTo(1);
    }

    /** Order-summary gồm cả các trạng thái không đóng góp doanh thu và tổng tiền từng nhóm chính xác. */
    @Test
    void orderSummaryIncludesAllStatuses() throws Exception {
        JsonNode report = report(get("/api/v1/reports/order-summary"));
        Map<OrderStatus, Baseline> added = Map.of(
                OrderStatus.CONFIRMED, new Baseline(3, new BigDecimal("1030")),
                OrderStatus.PACKED, new Baseline(1, new BigDecimal("60")),
                OrderStatus.SHIPPED, new Baseline(1, new BigDecimal("25")),
                OrderStatus.DELIVERED, new Baseline(2, new BigDecimal("55")),
                OrderStatus.PENDING, new Baseline(1, new BigDecimal("990")),
                OrderStatus.CANCELLED, new Baseline(1, new BigDecimal("990")),
                OrderStatus.EXPIRED, new Baseline(1, new BigDecimal("990")),
                OrderStatus.RETURNED, new Baseline(1, new BigDecimal("990")));
        for (JsonNode row : report) {
            OrderStatus status = OrderStatus.valueOf(row.get("order_status").asText());
            assertThat(row.get("total_count").asLong()).isEqualTo(baseline.get(status).count() + added.get(status).count());
            assertThat(row.get("total_amount").decimalValue())
                    .isEqualByComparingTo(baseline.get(status).amount().add(added.get(status).amount()));
        }
        assertThat(report.size()).isEqualTo(8);
    }

    /** Mọi endpoint phải chặn CUSTOMER/STAFF, đồng thời cho phép MANAGER/ADMIN qua JWT thật. */
    @ParameterizedTest
    @MethodSource("roleEndpointMatrix")
    void permissionsForEveryReport(String role, String endpoint, int expectedStatus) throws Exception {
        mvc.perform(get("/api/v1/reports/" + endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(user(role))))
                .andExpect(status().is(expectedStatus));
    }

    /** Ma trận bốn role và bốn endpoint để tránh bỏ sót route mới khi kiểm tra phân quyền. */
    private static Stream<Arguments> roleEndpointMatrix() {
        return Stream.of("MANAGER", "ADMIN", "CUSTOMER", "WAREHOUSE_STAFF")
                .flatMap(role -> Stream.of("revenue", "top-products", "low-stock", "order-summary")
                        .map(endpoint -> Arguments.of(role, endpoint,
                                role.equals("MANAGER") || role.equals("ADMIN") ? 200 : 403)));
    }

    /** Client chưa đăng nhập phải nhận 401 cho tất cả báo cáo. */
    @ParameterizedTest
    @ValueSource(strings = {"revenue", "top-products", "low-stock", "order-summary"})
    void anonymousDenied(String endpoint) throws Exception {
        mvc.perform(get("/api/v1/reports/" + endpoint)).andExpect(status().isUnauthorized());
    }

    /** Kiểm tra dữ liệu đầu vào để lỗi ngày, ngưỡng, enum và size không biến thành 500. */
    @Test
    void invalidParametersReturn400() throws Exception {
        for (String path : List.of(
                "revenue?fromDate=2040-02-01&toDate=2040-01-01",
                "revenue?fromDate=ngay-sai",
                "revenue?groupBy=YEAR",
                "low-stock?threshold=-1",
                "low-stock?warehouseId=0",
                "top-products?limit=0",
                "top-products?limit=101",
                "top-products?size=101",
                "top-products?limit=chu")) {
            mvc.perform(get("/api/v1/reports/" + path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(manager)))
                    .andExpect(status().isBadRequest());
        }
    }

    /** Kho hoặc khoảng ngày không có dữ liệu trả trang rỗng với tổng số nhóm bằng không. */
    @Test
    void emptyReportsHaveCorrectTotals() throws Exception {
        JsonNode revenue = report(get("/api/v1/reports/revenue")
                .param("fromDate", "2090-01-01").param("toDate", "2090-01-01"));
        assertThat(revenue.get("total_elements").asLong()).isZero();
        assertThat(revenue.get("content").size()).isZero();
        JsonNode top = report(get("/api/v1/reports/top-products")
                .param("fromDate", "2090-01-01").param("toDate", "2090-01-01"));
        assertThat(top.get("total_elements").asLong()).isZero();
        JsonNode stock = report(get("/api/v1/reports/low-stock").param("warehouseId", Long.toString(Long.MAX_VALUE)));
        assertThat(stock.get("total_elements").asLong()).isZero();
    }

    /** Thực thi HTTP với JWT quản lý và đọc response đã kiểm tra 200. */
    private JsonNode report(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(manager)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Tìm nhóm kho/ngày mà không giả định thứ tự mã kho của database. */
    private JsonNode findWarehousePeriod(JsonNode report, Long warehouseId, String period) {
        for (JsonNode row : report.get("content")) {
            if (row.get("warehouse_id").asLong() == warehouseId && row.get("period").asText().equals(period)) {
                return row;
            }
        }
        throw new AssertionError("Không tìm thấy nhóm doanh thu mong đợi.");
    }

    /** Tạo actor thật để JWT filter đọc role hiện tại trong database. */
    private User user(String role) {
        return users.save(new User(UUID.randomUUID() + "@example.com", "hash-kiem-thu",
                "Người kiểm thử báo cáo", roles.findByName(role).orElseThrow()));
    }

    /** Tạo snapshot đơn phục vụ báo cáo, không làm thay đổi ledger hoặc gọi lifecycle trong fixture. */
    private void addOrder(OrderStatus status, Warehouse warehouse, String createdAt, Map<Product, Integer> items) {
        BigDecimal total = items.entrySet().stream()
                .map(item -> item.getKey().getUnitPrice().multiply(BigDecimal.valueOf(item.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("code", UUID.randomUUID().toString())
                .addValue("customer", customer.getId())
                .addValue("warehouse", warehouse.getId())
                .addValue("status", status.name())
                .addValue("total", total)
                .addValue("createdAt", OffsetDateTime.parse(createdAt), Types.TIMESTAMP_WITH_TIMEZONE);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO orders (order_code, customer_id, warehouse_id, status, total_amount, created_at, updated_at)
                VALUES (:code, :customer, :warehouse, :status, :total, :createdAt, :createdAt)
                """, parameters, key, new String[]{"id"});
        Long orderId = key.getKey().longValue();
        for (Map.Entry<Product, Integer> item : items.entrySet()) {
            jdbc.update("""
                    INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
                    VALUES (:orderId, :productId, :quantity, :price, :lineTotal)
                    """, new MapSqlParameterSource()
                    .addValue("orderId", orderId)
                    .addValue("productId", item.getKey().getId())
                    .addValue("quantity", item.getValue())
                    .addValue("price", item.getKey().getUnitPrice())
                    .addValue("lineTotal", item.getKey().getUnitPrice().multiply(BigDecimal.valueOf(item.getValue()))));
        }
    }

    /** Seed tồn kho để kiểm tra ngưỡng cảnh báo và tổng available + reserved. */
    private void addInventory(Product product, Warehouse warehouse, int available, int reserved) {
        jdbc.update("""
                INSERT INTO inventories (product_id, warehouse_id, available_quantity, reserved_quantity)
                VALUES (:product, :warehouse, :available, :reserved)
                """, new MapSqlParameterSource()
                .addValue("product", product.getId())
                .addValue("warehouse", warehouse.getId())
                .addValue("available", available)
                .addValue("reserved", reserved));
    }

    /** Snapshot số liệu trước fixture để cộng kỳ vọng của order-summary độc lập với test khác. */
    private record Baseline(long count, BigDecimal amount) {
    }
}
