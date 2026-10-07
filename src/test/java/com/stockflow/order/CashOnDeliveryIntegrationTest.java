package com.stockflow.order;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static com.stockflow.order.support.CheckoutTestData.orderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.*;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"ghn.token=MOCK_TOKEN", "spring.datasource.url=jdbc:h2:mem:stockflow_cod;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CashOnDeliveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired InventoryService inventory;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;
    User customer, admin, staff, otherStaff;
    OrderResponse order;

    @BeforeEach void setup() {
        String key = UUID.randomUUID().toString();
        var category = categories.save(new Category(key, key));
        var product = products.save(new Product(category, key, "GHN item", new BigDecimal("10000"), ProductStatus.ACTIVE));
        var warehouse = warehouses.save(new Warehouse(key, "GHN warehouse", "Street, Ward, District, Province", WarehouseStatus.ACTIVE));
        var other = warehouses.save(new Warehouse(key + "-other", "Other", "Other address", WarehouseStatus.ACTIVE));
        customer = user("CUSTOMER"); admin = user("ADMIN"); staff = user("WAREHOUSE_STAFF"); otherStaff = user("WAREHOUSE_STAFF");
        jdbc.update("INSERT INTO warehouse_staff_assignments (user_id, warehouse_id) VALUES (?, ?)", staff.getId(), warehouse.getId());
        jdbc.update("INSERT INTO warehouse_staff_assignments (user_id, warehouse_id) VALUES (?, ?)", otherStaff.getId(), other.getId());
        inventory.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 20, "Initial"), admin.getId());
        order = orders.createOrder(orderRequest(warehouse.getId(), List.of(new CreateOrderRequest.Item(product.getId(), 2))), customer.getId());


    }
    private User user(String role) {
        return users.save(verifiedUser(UUID.randomUUID()+"@example.com", "hash", "Test", roles.findByName(role).orElseThrow()));
    }
    @org.springframework.boot.test.mock.mockito.SpyBean com.stockflow.shipping.client.GhnClient ghn;
    @Autowired com.stockflow.report.repository.ReportRepository reports;
    private String auth(User user) { return "Bearer " + jwt.generateToken(user); }
    private void confirm() throws Exception {
        mvc.perform(post("/api/v1/orders/" + order.id() + "/cod/confirm").header("Authorization", auth(customer)))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("CONFIRMED"));
    }
    private String paymentStatus() {
        return jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, order.id());
    }
    @Test void codRemainsUnpaidAndRepeatDoesNotDispatchAgain() throws Exception {
        confirm(); confirm();
        assertThat(paymentStatus()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT paid_at FROM payments WHERE order_id=?", Object.class, order.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=?", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE reference_type='ORDER' AND reference_id=? AND type='DISPATCH'", Integer.class, order.id())).isEqualTo(1);
    }
    @Test void shipsCodWithFullCollectionAmountAndCollectsOnDelivery() throws Exception {
        confirm(); orders.packOrder(order.id(), staff.getId());
        org.mockito.Mockito.doReturn(new com.stockflow.shipping.dto.GhnCreateOrderResponse(200,
            new com.stockflow.shipping.dto.GhnCreateOrderResponse.Data("COD-GHN-" + order.id())))
            .when(ghn).createShippingOrder(org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/v1/orders/" + order.id() + "/ghn-ship").header("Authorization", auth(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("SHIPPED"));
        var capture = org.mockito.ArgumentCaptor.forClass(com.stockflow.shipping.dto.GhnCreateOrderRequest.class);
        org.mockito.Mockito.verify(ghn).createShippingOrder(capture.capture());
        assertThat(capture.getValue().payload().get("cod_amount")).isEqualTo(20000);
        assertThat(paymentStatus()).isEqualTo("PENDING");
        orders.deliverOrder(order.id(), staff.getId());
        assertThat(paymentStatus()).isEqualTo("PAID");
        Object paidAt = jdbc.queryForObject("SELECT paid_at FROM payments WHERE order_id=?", Object.class, order.id());
        assertThat(paidAt).isNotNull();
        orders.deliverOrder(order.id(), staff.getId());
        assertThat(jdbc.queryForObject("SELECT paid_at FROM payments WHERE order_id=?", Object.class, order.id())).isEqualTo(paidAt);
    }
    @Test void otherCustomerCannotChooseCodOrReadPayment() throws Exception {
        User other = user("CUSTOMER");
        mvc.perform(post("/api/v1/orders/" + order.id() + "/cod/confirm").header("Authorization", auth(other))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/orders/" + order.id() + "/payment").header("Authorization", auth(other))).andExpect(status().isForbidden());
    }
    @Test void unpaidCodCancellationRestocksWithoutRefundingMoney() throws Exception {
        confirm(); orders.cancelOrder(order.id(), admin.getId());
        assertThat(paymentStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT physical_quantity FROM (SELECT available_quantity + reserved_quantity AS physical_quantity FROM inventories WHERE warehouse_id=?)", Integer.class, order.warehouseId())).isEqualTo(20);
    }
    @Test void paymentDetailsShowCodAndNoCollectionDate() throws Exception {
        confirm();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/orders/" + order.id() + "/payment").header("Authorization", auth(customer)))
            .andExpect(status().isOk()).andExpect(jsonPath("method").value("COD"))
            .andExpect(jsonPath("status").value("PENDING")).andExpect(jsonPath("amount").value(20000));
    }
    @Test void codCannotBeConvertedToSimulatedPayment() throws Exception {
        confirm();
        mvc.perform(post("/api/v1/orders/" + order.id() + "/payment-simulations/confirm").header("Authorization", auth(customer))).andExpect(status().isConflict());
        assertThat(paymentStatus()).isEqualTo("PENDING");
    }
    @Test void cancelledOrderCannotChooseCod() throws Exception {
        orders.cancelOrder(order.id(), customer.getId());
        mvc.perform(post("/api/v1/orders/" + order.id() + "/cod/confirm").header("Authorization", auth(customer))).andExpect(status().isConflict());
    }
    @Test void revenueCountsCodOnlyAfterDelivery() throws Exception {
        confirm();
        assertThat(reports.revenue(null, null, order.warehouseId(), com.stockflow.report.dto.ReportPeriod.DAY,
            org.springframework.data.domain.PageRequest.of(0,20)).getTotalElements()).isZero();
        orders.packOrder(order.id(), staff.getId()); orders.shipOrder(order.id(), null, staff.getId());
        orders.deliverOrder(order.id(), staff.getId());
        var revenue = reports.revenue(null, null, order.warehouseId(), com.stockflow.report.dto.ReportPeriod.DAY,
            org.springframework.data.domain.PageRequest.of(0,20));
        assertThat(revenue.getContent().get(0).totalRevenue()).isEqualByComparingTo("20000");
    }
}