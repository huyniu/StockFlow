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

@SpringBootTest(properties = {"ghn.token=MOCK_TOKEN", "spring.datasource.url=jdbc:h2:mem:stockflow_reviews;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductReviewIntegrationTest {
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
        orders.shipOrder(order.id(), new ShipOrderRequest("REVIEW-" + key), staff.getId());
        orders.deliverOrder(order.id(), staff.getId());
    }
    private User user(String role) {
        return users.save(verifiedUser(UUID.randomUUID()+"@example.com", "hash", "Test", roles.findByName(role).orElseThrow()));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder review(User actor, String body) {
        return post("/api/v1/orders/" + order.id() + "/reviews")
                .header("Authorization", "Bearer " + jwt.generateToken(actor))
                .contentType("application/json").content(body);
    }
    private String body() {
        return "{\"product_id\":" + order.items().get(0).productId() + ",\"rating\":5,\"service_rating\":4,\"comment\":\"  Sản phẩm tốt, giao nhanh  \"}";
    }
    @Test void deliveredPurchaseCanReviewAndPublicPageHasNoPrivateContactDetails() throws Exception {
        mvc.perform(review(customer, body())).andExpect(status().isCreated())
                .andExpect(jsonPath("rating").value(5)).andExpect(jsonPath("service_rating").value(4))
                .andExpect(jsonPath("comment").value("Sản phẩm tốt, giao nhanh"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products/" + order.items().get(0).productId() + "/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(1))
                .andExpect(jsonPath("average_rating").value(5.0)).andExpect(jsonPath("average_service_rating").value(4.0))
                .andExpect(jsonPath("content[0].customer_name").value("Test"))
                .andExpect(jsonPath("content[0].email").doesNotExist()).andExpect(jsonPath("content[0].customer_id").doesNotExist());
    }
    @Test void duplicateReviewIsRejected() throws Exception {
        mvc.perform(review(customer, body())).andExpect(status().isCreated());
        mvc.perform(review(customer, body())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_id=?", Long.class, order.id())).isEqualTo(1);
    }
    @Test void anotherCustomerCannotReviewOrReadPrivateOrderReviews() throws Exception {
        var other = user("CUSTOMER");
        mvc.perform(review(other, body())).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/orders/" + order.id() + "/reviews")
                .header("Authorization", "Bearer " + jwt.generateToken(other))).andExpect(status().isForbidden());
    }
    @Test void staffCannotPostCustomerReviews() throws Exception {
        mvc.perform(review(staff, body())).andExpect(status().isForbidden());
    }
    @Test void anonymousCannotPostReviews() throws Exception {
        mvc.perform(post("/api/v1/orders/" + order.id() + "/reviews").contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PENDING","CONFIRMED","PACKED","SHIPPED","CANCELLED","EXPIRED","RETURNED"})
    void onlyDeliveredOrdersCanReview(String status) throws Exception {
        jdbc.update("UPDATE orders SET status=? WHERE id=?", status, order.id());
        mvc.perform(review(customer, body())).andExpect(status().isConflict());
    }
    @Test void productMustBelongToPurchase() throws Exception {
        String key=UUID.randomUUID().toString();
        var category=categories.save(new Category(key,key));
        var foreign=products.save(new Product(category,key,"Not purchased",new BigDecimal("100"),ProductStatus.ACTIVE));
        mvc.perform(review(customer, body().replace("\"product_id\":" + order.items().get(0).productId(), "\"product_id\":" + foreign.getId())))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsOutOfRangeStarsAndBlankComments() throws Exception {
        mvc.perform(review(customer, body().replace("\"rating\":5", "\"rating\":6"))).andExpect(status().isBadRequest());
        mvc.perform(review(customer, body().replace("\"service_rating\":4", "\"service_rating\":0"))).andExpect(status().isBadRequest());
        mvc.perform(review(customer, body().replace("  Sản phẩm tốt, giao nhanh  ", "   "))).andExpect(status().isBadRequest());
    }
    @Test void customerCanReadPreviouslySubmittedReview() throws Exception {
        mvc.perform(review(customer, body())).andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/orders/" + order.id() + "/reviews")
                .header("Authorization", "Bearer " + jwt.generateToken(customer))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].product_id").value(order.items().get(0).productId().intValue()));
    }
    @Test void emptyProductReviewsAndInvalidPageAreHandled() throws Exception {
        String path="/api/v1/products/"+order.items().get(0).productId()+"/reviews";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("total_elements").value(0)).andExpect(jsonPath("average_rating").value(0.0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("page","-1"))
                .andExpect(status().isBadRequest());
    }
    @Test void skuReviewAlsoAppearsOnItsModelPage() throws Exception {
        String key=UUID.randomUUID().toString();
        var category=categories.save(new Category(key,key));
        var root=products.save(new Product(category,key,"Model",new BigDecimal("100"),ProductStatus.ACTIVE));
        jdbc.update("INSERT INTO product_versions(product_id,name,name_key) VALUES (?,?,?)", root.getId(),"Version",key);
        Long version=jdbc.queryForObject("SELECT id FROM product_versions WHERE product_id=?",Long.class,root.getId());
        jdbc.update("INSERT INTO product_variants(product_id,sku_product_id,color_name,color_key,version_id) VALUES (?,?,?,?,?)",
                root.getId(),order.items().get(0).productId(),"Black",key,version);
        mvc.perform(review(customer,body())).andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products/"+root.getId()+"/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(1))
                .andExpect(jsonPath("content[0].product_id").value(order.items().get(0).productId().intValue()));
    }
    @Test void publicReviewsArePaginatedAndSummaryCoversAllPages() throws Exception {
        for(int i=0;i<24;i++) {
            String code="REVIEW-PAGE-"+UUID.randomUUID();
            jdbc.update("INSERT INTO orders(order_code,customer_id,warehouse_id,status,total_amount,created_at,updated_at) VALUES (?,?,?,'DELIVERED',100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    code,customer.getId(),order.warehouseId());
            Long id=jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?",Long.class,code);
            jdbc.update("INSERT INTO order_items(order_id,product_id,quantity,unit_price,line_total) VALUES (?,?,1,100,100)",id,order.items().get(0).productId());
            jdbc.update("INSERT INTO product_reviews(order_id,product_id,customer_id,rating,service_rating,comment) VALUES (?,?,?,5,4,?)",id,order.items().get(0).productId(),customer.getId(),"Review "+i);
        }
        String path="/api/v1/products/"+order.items().get(0).productId()+"/reviews";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("total_elements").value(24)).andExpect(jsonPath("content.length()").value(20));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("page","1")).andExpect(status().isOk())
                .andExpect(jsonPath("content.length()").value(4)).andExpect(jsonPath("average_rating").value(5.0));
    }
}
