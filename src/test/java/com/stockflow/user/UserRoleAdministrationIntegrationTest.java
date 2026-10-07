package com.stockflow.user;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:stockflow_user_roles;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class UserRoleAdministrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired WarehouseRepository warehouses;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    User admin, customer;
    Warehouse a,b;
    User user(String role) { return users.save(verifiedUser(UUID.randomUUID()+"@example.com","hash","Role test",roles.findByName(role).orElseThrow())); }
    String auth(User user) { return "Bearer "+jwt.generateToken(user); }
    @BeforeEach void setup() {
        admin=user("ADMIN"); customer=user("CUSTOMER");
        a=warehouses.save(new Warehouse(UUID.randomUUID().toString(),"A","Address",WarehouseStatus.ACTIVE));
        b=warehouses.save(new Warehouse(UUID.randomUUID().toString(),"B","Address",WarehouseStatus.ACTIVE));
    }
    org.springframework.test.web.servlet.ResultActions change(User actor,Long id,String role,String ids) throws Exception {
        return mvc.perform(patch("/api/v1/admin/users/"+id+"/role").header("Authorization",auth(actor)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\""+role+"\",\"warehouse_ids\":"+ids+"}"));
    }
    String ids(Warehouse w) { return "["+w.getId()+"]"; }
    int auditCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM user_role_audit WHERE target_user_id=?",Integer.class,customer.getId()); }
    @Test void existingTokenUsesNewRoleAndAssignedWarehouseImmediately() throws Exception {
        String token=auth(customer);
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(a)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("role").value("WAREHOUSE_STAFF"));
        mvc.perform(get("/api/v1/warehouses/operating-options").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(a.getId()));
        assertThat(users.findById(customer.getId()).orElseThrow().getPasswordHash()).isEqualTo("hash");
    }
    @Test void reassignmentRemovesOldWarehouseAndAuditsActor() throws Exception {
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(a)).andExpect(status().isOk());
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(b)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users/"+customer.getId()+"/permissions").header("Authorization",auth(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("warehouse_ids[0]").value(b.getId())).andExpect(jsonPath("warehouse_ids.length()").value(1));
        mvc.perform(get("/api/v1/admin/users/"+customer.getId()+"/role-history").header("Authorization",auth(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].actor_id").value(admin.getId()))
            .andExpect(jsonPath("$[0].old_warehouse_ids[0]").value(a.getId())).andExpect(jsonPath("$[0].new_warehouse_ids[0]").value(b.getId()));
    }
    @Test void leavingStaffRemovesAssignmentsAndRevokesWarehouseAccess() throws Exception {
        String token=auth(customer);
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(a)).andExpect(status().isOk());
        change(admin,customer.getId(),"MANAGER","[]").andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT warehouse_id FROM warehouse_staff_assignments WHERE user_id=?",Long.class,customer.getId())).isEmpty();
        change(admin,customer.getId(),"CUSTOMER","[]").andExpect(status().isOk());
        mvc.perform(get("/api/v1/warehouses").header("Authorization",token)).andExpect(status().isForbidden());
    }
    @Test void onlyAdminCanChangeOrReadPermissionsAndHistory() throws Exception {
        for(String role: java.util.List.of("CUSTOMER","MANAGER","WAREHOUSE_STAFF")) {
            User actor=user(role);
            change(actor,customer.getId(),"MANAGER","[]").andExpect(status().isForbidden());
            for(String path:java.util.List.of("permissions","role-history")) mvc.perform(get("/api/v1/admin/users/"+customer.getId()+"/"+path).header("Authorization",auth(actor))).andExpect(status().isForbidden());
        }
    }
    @Test void protectsAdministratorsAndSystemAccount() throws Exception {
        change(admin,admin.getId(),"CUSTOMER","[]").andExpect(status().isConflict());
        change(admin,user("ADMIN").getId(),"MANAGER","[]").andExpect(status().isConflict());
        change(admin,users.findByEmail("inventory-expiry@stockflow.invalid").orElseThrow().getId(),"MANAGER","[]").andExpect(status().isConflict());
        change(admin,customer.getId(),"ADMIN","[]").andExpect(status().isBadRequest());
    }
    @Test void validatesRoleAndWarehouseCombination() throws Exception {
        change(admin,customer.getId(),"WAREHOUSE_STAFF","[]").andExpect(status().isBadRequest());
        change(admin,customer.getId(),"MANAGER",ids(a)).andExpect(status().isBadRequest());
        change(admin,customer.getId(),"WAREHOUSE_STAFF","["+a.getId()+","+a.getId()+"]").andExpect(status().isBadRequest());
        change(admin,customer.getId(),"UNKNOWN","[]").andExpect(status().isBadRequest());
        assertThat(auditCount()).isZero();
    }
    @Test void invalidOrInactiveWarehouseDoesNotPartiallyChangeRole() throws Exception {
        Warehouse inactive=warehouses.save(new Warehouse(UUID.randomUUID().toString(),"Inactive","Address",WarehouseStatus.INACTIVE));
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(inactive)).andExpect(status().isBadRequest());
        change(admin,customer.getId(),"WAREHOUSE_STAFF","["+a.getId()+",9223372036854775807]").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT r.name FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=?",String.class,customer.getId())).isEqualTo("CUSTOMER");
        assertThat(auditCount()).isZero();
    }
    @Test void repeatedIdenticalChangeDoesNotDuplicateAudit() throws Exception {
        change(admin,customer.getId(),"CUSTOMER","[]").andExpect(status().isOk());
        assertThat(auditCount()).isZero();
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(a)).andExpect(status().isOk());
        change(admin,customer.getId(),"WAREHOUSE_STAFF",ids(a)).andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(1);
    }
}
