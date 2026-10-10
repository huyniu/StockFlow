package com.stockflow.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.auth.service.OperatorAccountRecoveryRunner;
import com.stockflow.auth.service.OperatorAccountRecoveryService;
import com.stockflow.demo.DemoDataSeeder;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.DeliveryDetailsRequest;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.repository.UserRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:operator_recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "app.demo.seed-catalog=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@Transactional
class OperatorAccountRecoveryIntegrationTest {
    static final List<String> EMAILS = List.of("admin@stockflow.com", "manager@stockflow.com", "staff.hn@stockflow.com");
    @Autowired OperatorAccountRecoveryService recovery;
    @Autowired UserRepository users;
    @Autowired DemoDataSeeder seeder;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @Autowired OrderService orders;

    Map<String, String> privatePasswords() {
        var result = new LinkedHashMap<String, String>();
        EMAILS.forEach(email -> result.put(email, UUID.randomUUID().toString() + "!Q9"));
        return result;
    }
    String requestId() { return UUID.randomUUID().toString(); }
    String login(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(response.getResponse().getContentAsString()).path("access_token").asText();
    }

    @ParameterizedTest
    @CsvSource({"admin@stockflow.com,ADMIN,200,200", "manager@stockflow.com,MANAGER,403,200",
            "staff.hn@stockflow.com,WAREHOUSE_STAFF,403,403"})
    void recoveredOperatorsLoginWithTheirOriginalRoleAndOldTokensAreRejected(
            String email, String role, int userStatus, int reportStatus) throws Exception {
        var before = users.findByEmail(email).orElseThrow();
        long id = before.getId(), version = before.getAuthVersion();
        String stale = "Bearer " + jwt.generateToken(before);
        var passwords = privatePasswords();
        assertThat(recovery.recover(requestId(), passwords)).isTrue();
        var after = users.findByEmail(email).orElseThrow();
        assertThat(after.getId()).isEqualTo(id);
        assertThat(after.getRole().getName()).isEqualTo(role);
        assertThat(after.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(after.getAuthVersion()).isEqualTo(version + 1);
        assertThat(after.getOperatorRecoveredAt()).isNotNull();
        String bearer = login(email, passwords.get(email));
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer)).andExpect(status().is(userStatus));
        mvc.perform(get("/api/v1/reports/order-summary").header("Authorization", bearer)).andExpect(status().is(reportStatus));
        long assignedWarehouse = jdbc.queryForObject("SELECT id FROM warehouses WHERE code='WH-HAN-01'", Long.class);
        mvc.perform(get("/api/v1/inventories").param("warehouseId", Long.toString(assignedWarehouse))
                .header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", stale)).andExpect(status().isUnauthorized());
        String oldPassword = role.equals("ADMIN") ? "Admin@123" : role.equals("MANAGER") ? "Manager@123" : "Staff@123";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", oldPassword))))
                .andExpect(status().isUnauthorized());
        after.setStatus(UserStatus.INACTIVE); users.saveAndFlush(after);
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test void recoveryPreservesOrdersLedgerAndWarehouseAssignments() {
        long customer = users.findByEmail("customer@stockflow.com").orElseThrow().getId();
        long warehouse = jdbc.queryForObject("SELECT id FROM warehouses WHERE code='WH-HAN-01'", Long.class);
        long product = jdbc.queryForObject("SELECT MIN(id) FROM products", Long.class);
        var order = orders.createOrder(new CreateOrderRequest(warehouse, List.of(new CreateOrderRequest.Item(product, 1)),
                new DeliveryDetailsRequest("QA", "0901234567", "Local QA address", null)), customer);
        var ids = jdbc.queryForList("SELECT id,email,role_id FROM users ORDER BY id");
        var ledger = jdbc.queryForList("SELECT * FROM inventory_movements ORDER BY id");
        var assignments = jdbc.queryForList("SELECT * FROM warehouse_staff_assignments ORDER BY user_id,warehouse_id");
        var ordersBefore = jdbc.queryForList("SELECT * FROM orders ORDER BY id");
        recovery.recover(requestId(), privatePasswords());
        assertThat(jdbc.queryForList("SELECT id,email,role_id FROM users ORDER BY id")).isEqualTo(ids);
        assertThat(jdbc.queryForList("SELECT * FROM inventory_movements ORDER BY id")).isEqualTo(ledger);
        assertThat(jdbc.queryForList("SELECT * FROM warehouse_staff_assignments ORDER BY user_id,warehouse_id")).isEqualTo(assignments);
        assertThat(jdbc.queryForList("SELECT * FROM orders ORDER BY id")).isEqualTo(ordersBefore);
        assertThat(orders.getOrder(order.id(), customer).id()).isEqualTo(order.id());
    }

    @Test void startupRunnersAndReplayDoNotResetPasswordsReactivateOrChangeAssignments() throws Exception {
        String id = requestId(); var passwords = privatePasswords();
        recovery.recover(id, passwords);
        var staff = users.findByEmail(EMAILS.get(2)).orElseThrow();
        jdbc.update("DELETE FROM warehouse_staff_assignments WHERE user_id=?", staff.getId());
        long other = jdbc.queryForObject("SELECT id FROM warehouses WHERE code='WH-DAD-01'", Long.class);
        jdbc.update("INSERT INTO warehouse_staff_assignments(user_id,warehouse_id) VALUES (?,?)", staff.getId(), other);
        staff.setStatus(UserStatus.INACTIVE); users.saveAndFlush(staff);
        var before = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        var assignments = jdbc.queryForList("SELECT * FROM warehouse_staff_assignments ORDER BY user_id,warehouse_id");
        seeder.seed();
        // Recreate the recovery runner as at a restart, even after the owner removes passwords.
        var env = new MockEnvironment().withProperty("OPERATOR_RECOVERY_REQUEST_ID", id);
        new OperatorAccountRecoveryRunner(recovery, env).run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM warehouse_staff_assignments ORDER BY user_id,warehouse_id")).isEqualTo(assignments);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_recovery_runs WHERE request_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(context.containsBean("operatorAccountRecoveryRunner")).isFalse();
    }

    @Test void staffCannotOperateAnotherWarehouseAfterRecovery() throws Exception {
        var passwords = privatePasswords(); recovery.recover(requestId(), passwords);
        String bearer = login(EMAILS.get(2), passwords.get(EMAILS.get(2)));
        long other = jdbc.queryForObject("SELECT id FROM warehouses WHERE code='WH-DAD-01'", Long.class);
        long product = jdbc.queryForObject("SELECT MIN(id) FROM products", Long.class);
        mvc.perform(post("/api/v1/inventories/stock-in").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("warehouse_id", other, "product_id", product, "quantity", 1))))
                .andExpect(status().isForbidden());
    }

    @Test void invalidOrReusedPasswordsDoNotPartiallyRecover() {
        var before = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        var passwords = privatePasswords(); passwords.put(EMAILS.get(2), "Staff@123");
        assertThatThrownBy(() -> recovery.recover(requestId(), passwords)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(before);
        passwords.put(EMAILS.get(2), passwords.get(EMAILS.get(0)));
        assertThatThrownBy(() -> recovery.recover(requestId(), passwords)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(before);
    }

    @Test void wrongRoleOrUnverifiedEmailIsNotSilentlyOverridden() {
        var admin = users.findByEmail(EMAILS.get(0)).orElseThrow();
        admin.setEmailVerified(false); users.saveAndFlush(admin);
        assertThatThrownBy(() -> recovery.recover(requestId(), privatePasswords())).isInstanceOf(IllegalStateException.class);
        assertThat(admin.isEmailVerified()).isFalse();
        assertThat(admin.getOperatorRecoveredAt()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentRecoveryWithSameRequestRunsOnlyOnce() throws Exception {
        String id = requestId(); var passwords = privatePasswords();
        var versions = EMAILS.stream().map(email -> users.findByEmail(email).orElseThrow().getAuthVersion()).toList();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> job = () -> { start.await(); return recovery.recover(id, passwords); };
            var first = pool.submit(job); var second = pool.submit(job); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            for (int i = 0; i < EMAILS.size(); i++)
                assertThat(users.findByEmail(EMAILS.get(i)).orElseThrow().getAuthVersion()).isEqualTo(versions.get(i) + 1);
        } finally { pool.shutdownNow(); }
    }
}
