package com.stockflow.user;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:stockflow_user_admin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdministrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    User admin, customer;
    User user(String role) { return users.save(verifiedUser(UUID.randomUUID()+"@example.com","hash","Admin test",roles.findByName(role).orElseThrow())); }
    String auth(User user) { return "Bearer "+jwt.generateToken(user); }
    @BeforeEach void setup() { admin=user("ADMIN"); customer=user("CUSTOMER"); }
    org.springframework.test.web.servlet.ResultActions changeStatus(Long id, String value) throws Exception {
        return mvc.perform(patch("/api/v1/admin/users/"+id+"/status").header("Authorization",auth(admin))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\""+value+"\"}"));
    }
    @Test void adminCanSearchFilterAndReadSafeProfile() throws Exception {
        mvc.perform(get("/api/v1/admin/users").header("Authorization",auth(admin)).param("q",customer.getEmail()).param("role","CUSTOMER").param("status","ACTIVE"))
            .andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(1))
            .andExpect(jsonPath("content[0].id").value(customer.getId())).andExpect(jsonPath("content[0].password_hash").doesNotExist());
        mvc.perform(get("/api/v1/admin/users/"+customer.getId()).header("Authorization",auth(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("email").value(customer.getEmail())).andExpect(jsonPath("passwordHash").doesNotExist());
    }
    @Test void locksImmediatelyAndRestoresAccessWithoutChangingCredentials() throws Exception {
        String token=auth(customer);
        changeStatus(customer.getId(),"INACTIVE").andExpect(status().isOk()).andExpect(jsonPath("status").value("INACTIVE"));
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        changeStatus(customer.getId(),"ACTIVE").andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isOk());
        assertThat(users.findById(customer.getId()).orElseThrow().getPasswordHash()).isEqualTo("hash");
    }
    @Test void nonAdminCannotListReadOrModifyAccounts() throws Exception {
        for(String role : java.util.List.of("CUSTOMER","WAREHOUSE_STAFF","MANAGER")) {
            User actor=user(role);
            mvc.perform(get("/api/v1/admin/users").header("Authorization",auth(actor))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/users/"+customer.getId()).header("Authorization",auth(actor))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/admin/users/"+customer.getId()+"/status").header("Authorization",auth(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}")).andExpect(status().isForbidden());
        }
    }
    @Test void protectsSelfAndOtherAdministrators() throws Exception {
        changeStatus(admin.getId(),"INACTIVE").andExpect(status().isConflict());
        changeStatus(user("ADMIN").getId(),"INACTIVE").andExpect(status().isConflict());
    }
    @Test void protectsSystemAccount() throws Exception {
        changeStatus(users.findByEmail("inventory-expiry@stockflow.invalid").orElseThrow().getId(),"ACTIVE").andExpect(status().isConflict());
    }
    @Test void validatesPaginationRolesAndStatus() throws Exception {
        mvc.perform(get("/api/v1/admin/users").header("Authorization",auth(admin)).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").header("Authorization",auth(admin)).param("role","SUPER_ADMIN")).andExpect(status().isBadRequest());
        changeStatus(customer.getId(),"UNKNOWN").andExpect(status().isBadRequest());
    }
    @Test void unknownAccountReturnsNotFound() throws Exception { changeStatus(Long.MAX_VALUE,"INACTIVE").andExpect(status().isNotFound()); }
    @Test void guestCannotListAccounts() throws Exception { mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized()); }
}
