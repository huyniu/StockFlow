package com.stockflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.domain.ProductVariant;
import com.stockflow.catalog.domain.ProductVersion;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.repository.ProductVariantRepository;
import com.stockflow.catalog.repository.ProductVersionRepository;
import com.stockflow.order.domain.DeliveryDetails;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.domain.Payment;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.order.repository.PaymentRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm chứng xếp hạng công khai bằng doanh số thật, gộp model và không công bố thông tin tài chính. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:discovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontDiscoveryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVersionRepository versions;
    @Autowired ProductVariantRepository variants;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired WarehouseRepository warehouses;
    Category category;
    User customer;
    Warehouse warehouse;

    /** Database riêng và rollback từng ca giữ bảng xếp hạng độc lập với các test đặt hàng khác. */
    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString();
        category = categories.save(new Category("Danh mục bán chạy " + key, key));
        customer = users.save(new User(key + "@test.vn", "hash", "Khách bán chạy",
                roles.findByName("CUSTOMER").orElseThrow()));
        warehouse = warehouses.save(new Warehouse(key, "Kho bán chạy", "Địa chỉ kiểm thử",
                WarehouseStatus.ACTIVE));
    }

    /** Không bịa thứ hạng khi chưa có đơn đã thanh toán. */
    @Test
    void noSalesReturnsEmptyList() throws Exception {
        product("Sản phẩm chưa bán");
        assertThat(result(8)).isEmpty();
    }

    /** Số lượng quyết định thứ hạng; SKU màu được gộp về đúng một thẻ model. */
    @Test
    void ranksByQuantityAndGroupsColors() throws Exception {
        Product model = product("Điện thoại hai màu");
        Product blue = product("SKU xanh");
        Product rival = product("Laptop");
        ProductVersion version = versions.save(new ProductVersion(model, "256 GB"));
        variants.save(new ProductVariant(model, model, version, "Đen", "#000000"));
        variants.save(new ProductVariant(model, blue, version, "Xanh", "#2563EB"));
        sale(model, 2, OrderStatus.CONFIRMED, true, false);
        sale(blue, 3, OrderStatus.DELIVERED, true, false);
        sale(rival, 4, OrderStatus.PACKED, true, false);
        assertThat(ids(result(8))).containsExactly(model.getId(), rival.getId());
        assertThat(ids(result(1))).containsExactly(model.getId());
    }

    /** Chỉ bốn trạng thái đã trả tiền còn hiệu lực đóng góp vào số lượng bán. */
    @Test
    void excludesUnpaidReturnedCancelledAndRefundedSales() throws Exception {
        Product valid = product("Đơn hợp lệ");
        for (OrderStatus state : List.of(OrderStatus.CONFIRMED, OrderStatus.PACKED,
                OrderStatus.SHIPPED, OrderStatus.DELIVERED)) {
            sale(valid, 1, state, true, false);
        }
        sale(product("Chờ trả tiền"), 100, OrderStatus.PENDING, false, false);
        sale(product("Không có payment"), 100, OrderStatus.CONFIRMED, false, false);
        sale(product("Đã hủy"), 100, OrderStatus.CANCELLED, true, false);
        sale(product("Đã trả"), 100, OrderStatus.RETURNED, true, false);
        sale(product("Đã hoàn tiền"), 100, OrderStatus.CONFIRMED, true, true);
        assertThat(ids(result(8))).containsExactly(valid.getId());
    }

    /** Ngừng bán model hoặc xóa mọi cấu hình sẽ ẩn thẻ nhưng giữ lịch sử doanh số. */
    @Test
    void hidesInactiveModelsAndArchivedConfigurations() throws Exception {
        Product inactive = product("Model ngừng bán");
        inactive.update(null, null, ProductStatus.INACTIVE);
        sale(inactive, 10, OrderStatus.DELIVERED, true, false);
        Product archived = product("Màu đã xóa");
        ProductVersion version = versions.save(new ProductVersion(archived, "Cấu hình cũ"));
        ProductVariant color = variants.save(new ProductVariant(archived, archived, version, "Đen", null));
        color.archive();
        sale(archived, 10, OrderStatus.DELIVERED, true, false);
        assertThat(result(8)).isEmpty();
    }

    /** Thứ hạng bằng nhau ổn định theo ID và response chỉ có trường catalog công khai. */
    @Test
    void publicResponseUsesStableOrderAndCurrentCatalogPrice() throws Exception {
        Product first = product("Model thứ nhất");
        Product second = product("Model thứ hai");
        sale(second, 2, OrderStatus.DELIVERED, true, false);
        sale(first, 2, OrderStatus.DELIVERED, true, false);
        first.update(null, new BigDecimal("123.00"), null);
        JsonNode response = result(8);
        assertThat(ids(response)).containsExactly(first.getId(), second.getId());
        assertThat(response.get(0).get("unit_price").decimalValue()).isEqualByComparingTo("123.00");
        for (String privateField : List.of("total_revenue", "sold_quantity", "customer_id", "order_id")) {
            assertThat(response.get(0).has(privateField)).isFalse();
        }
    }

    /** Giới hạn được kiểm tra ở server, không cho tải danh sách vô hạn bằng query tùy ý. */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 13, 100})
    void rejectsInvalidLimit(int limit) throws Exception {
        mvc.perform(get("/api/v1/storefront/bestsellers").param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest());
    }

    /** Tạo sản phẩm riêng cho mỗi ca, không phụ thuộc SKU hoặc dữ liệu demo. */
    private Product product(String name) {
        return products.save(new Product(category, UUID.randomUUID().toString(), name,
                new BigDecimal("10.00"), ProductStatus.ACTIVE));
    }

    /** Seed dữ liệu thống kê trực tiếp; đây không phải giả lập luồng nhập/xuất tồn kho. */
    private void sale(Product product, int quantity, OrderStatus status, boolean paid, boolean refunded) {
        Order order = new Order(customer.getId(), warehouse.getId(), Instant.now(),
                new DeliveryDetails("Khách kiểm thử", "0901234567", "Địa chỉ test", null));
        order.addItem(product.getId(), quantity, product.getUnitPrice());
        order.changeStatus(status);
        orders.saveAndFlush(order);
        if (paid) {
            Payment payment = new Payment(order.getId(), order.getTotalAmount());
            if (refunded) payment.refund();
            payments.saveAndFlush(payment);
        }
    }

    /** Gọi endpoint không có JWT để kiểm chứng khách vãng lai xem được. */
    private JsonNode result(int limit) throws Exception {
        products.flush();
        variants.flush();
        String content = mvc.perform(get("/api/v1/storefront/bestsellers")
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    /** Chỉ so sánh thứ tự model, không phụ thuộc nội dung ảnh hoặc serializer danh mục. */
    private List<Long> ids(JsonNode response) {
        var values = new java.util.ArrayList<Long>();
        response.forEach(product -> values.add(product.get("id").asLong()));
        return values;
    }
}
