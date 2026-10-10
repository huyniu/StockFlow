package com.stockflow.auth;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.demo.DemoDataSeeder;
import com.stockflow.user.domain.*;
import com.stockflow.user.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:public_demo_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles({"test","demo"})
class PublicDemoAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataSeeder seeder;
    User customer,admin;

    @BeforeEach void setup(){
        customer=users.save(verifiedUser(UUID.randomUUID()+"@private.test",encoder.encode("Private@123"),"Khách riêng",roles.findByName("CUSTOMER").orElseThrow()));
        admin=users.save(verifiedUser(UUID.randomUUID()+"@private.test",encoder.encode("Private@123"),"Admin riêng",roles.findByName("ADMIN").orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings={"admin@stockflow.com","manager@stockflow.com","staff.hn@stockflow.com"})
    void publicOperatorCannotLoginOrUseOldJwtEvenIfReactivated(String email) throws Exception {
        User demo=users.findByEmail(email).orElseThrow();
        assertThat(demo.getStatus()).isEqualTo(UserStatus.INACTIVE);
        // Token có chữ ký và auth_version hợp lệ: backend phải chặn riêng định danh demo.
        demo.setStatus(UserStatus.ACTIVE);demo.setPasswordHash(encoder.encode("Changed@123"));users.saveAndFlush(demo);
        String bearer="Bearer "+jwt.generateToken(demo);
        assertThat(jwt.validateToken(bearer.substring(7))).isTrue();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email",email,"password","Changed@123"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.access_token").doesNotExist());
        for(String path:List.of("/api/v1/users/me","/api/v1/admin/users","/api/v1/admin/users/"+customer.getId(),
                "/api/v1/admin/users/"+customer.getId()+"/permissions","/api/v1/orders","/api/v1/inventories")){
            mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isUnauthorized());
        }
        mvc.perform(patch("/api/v1/admin/users/"+customer.getId()+"/status").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/"+customer.getId()+"/role").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\",\"warehouse_ids\":[]}"))
                .andExpect(status().isUnauthorized());
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements",Integer.class);
        mvc.perform(post("/api/v1/inventories/stock-in").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"product_id\":1,\"warehouse_id\":1,\"quantity\":10}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements",Integer.class)).isEqualTo(before);
        User unchanged=users.findByEmail(customer.getEmail()).orElseThrow();
        assertThat(unchanged.getRole().getName()).isEqualTo("CUSTOMER");assertThat(unchanged.getStatus()).isEqualTo(UserStatus.ACTIVE);
        seeder.seed();assertThat(users.findByEmail(email).orElseThrow().getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test void publicCustomerCannotReadPrivateUsersAddressesOrChangePermissions() throws Exception {
        jdbc.update("""
                INSERT INTO customer_addresses(user_id,label,recipient_name,recipient_phone,province_id,province_name,
                  district_id,district_name,ward_code,ward_name,street_address)
                VALUES (?,'Nhà riêng','Khách riêng','0901234567',201,'Hà Nội',1450,'Nam Từ Liêm','20907','Mễ Trì','Địa chỉ riêng QA')
                """,customer.getId());
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization","Bearer "+jwt.generateToken(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].street_address").value("Địa chỉ riêng QA"));
        var login=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"customer@stockflow.com\",\"password\":\"Customer@123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("CUSTOMER")).andReturn();
        String bearer="Bearer "+json.readTree(login.getResponse().getContentAsString()).path("access_token").asText();
        mvc.perform(get("/api/v1/admin/users").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users/"+customer.getId()).header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/"+customer.getId()+"/status").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/"+customer.getId()+"/role").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\",\"warehouse_ids\":[]}"))
                .andExpect(status().isForbidden());
        // Địa chỉ chỉ được đọc từ principal hiện tại, tham số user_id không đổi chủ sở hữu.
        mvc.perform(get("/api/v1/users/me/addresses").param("user_id",customer.getId().toString()).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        long addressId=jdbc.queryForObject("SELECT id FROM customer_addresses WHERE user_id=?",Long.class,customer.getId());
        mvc.perform(delete("/api/v1/users/me/addresses/"+addressId).header("Authorization",bearer)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_addresses WHERE id=?",Integer.class,addressId)).isEqualTo(1);
    }

    @Test void promotingPublicCustomerDoesNotGrantPublicOperatorAccess() throws Exception {
        var demo=users.findByEmail("customer@stockflow.com").orElseThrow();
        demo.setRole(roles.findByName("MANAGER").orElseThrow());users.saveAndFlush(demo);
        try{
            mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+jwt.generateToken(demo))).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"customer@stockflow.com\",\"password\":\"Customer@123\"}"))
                    .andExpect(status().isUnauthorized());
        }finally{demo.setRole(roles.findByName("CUSTOMER").orElseThrow());users.saveAndFlush(demo);}
    }

    @Test void privateAdminRetainsUserAdministrationAndPrivateCustomerRetainsLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email",customer.getEmail(),"password","Private@123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty());
        String bearer="Bearer "+jwt.generateToken(admin);
        mvc.perform(get("/api/v1/admin/users/"+customer.getId()).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(customer.getEmail()));
        mvc.perform(patch("/api/v1/admin/users/"+customer.getId()+"/role").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\",\"warehouse_ids\":[]}"))
                .andExpect(status().isOk());
        assertThat(users.findByEmail(customer.getEmail()).orElseThrow().getRole().getName()).isEqualTo("MANAGER");
    }
}
