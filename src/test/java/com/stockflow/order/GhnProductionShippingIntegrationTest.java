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

@SpringBootTest(properties = {"ghn.token=MOCK_TOKEN", "ghn.production=true", "spring.datasource.url=jdbc:h2:mem:stockflow_ghn_production;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GhnProductionShippingIntegrationTest {
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
        orders.confirmPaymentSimulation(order.id(), customer.getId());
        orders.packOrder(order.id(), staff.getId());
    }
    private User user(String role) {
        return users.save(verifiedUser(UUID.randomUUID()+"@example.com", "hash", "Test", roles.findByName(role).orElseThrow()));
    }
    @Test void failedCarrierCallKeepsPackedOrderAndDoesNotRecordShipment() throws Exception {
        String previousTracking = orders.getOrder(order.id(), admin.getId()).shipment().trackingCode();
        mvc.perform(post("/api/v1/orders/" + order.id() + "/ghn-ship")
                .header("Authorization", "Bearer " + jwt.generateToken(staff)))
                .andExpect(status().isServiceUnavailable());
        assertThat(orders.getOrder(order.id(), admin.getId()).status().name()).isEqualTo("PACKED");
        assertThat(orders.getOrder(order.id(), admin.getId()).shipment().trackingCode()).isEqualTo(previousTracking);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_dispatch_events WHERE order_id=?", Long.class, order.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE reference_type='ORDER' AND reference_id=? AND type='DISPATCH'", Long.class, order.id())).isEqualTo(1);
    }
    @Test void publicCheckoutReportsProductionMode() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/locations/mode"))
                .andExpect(status().isOk()).andExpect(jsonPath("test_mode").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/locations/provinces"))
                .andExpect(status().isServiceUnavailable());
    }
}
