package com.stockflow.user;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.user.repository.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"ghn.token=MOCK_TOKEN", "spring.datasource.url=jdbc:h2:mem:stockflow_default_address;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DefaultAddressIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    com.stockflow.user.domain.User user;
    String token;
    @BeforeEach void setup() {
        user=users.save(verifiedUser(UUID.randomUUID()+"@example.com", "hash", "Test", roles.findByName("CUSTOMER").orElseThrow()));
        token="Bearer "+jwt.generateToken(user);
    }
    Map<String,Object> address(int province, int district, String ward) {
        return Map.of("province_id",province,"district_id",district,"ward_code",ward,"street_address","12 Mễ Trì");
    }
    org.springframework.test.web.servlet.ResultActions update(Map<String,Object> body) throws Exception {
        return mvc.perform(patch("/api/v1/users/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    @Test void persistsCanonicalGhnAddressAndReturnsItOnNextRead() throws Exception {
        update(Map.of("default_address",address(201,1450,"20907"))).andExpect(status().isOk())
            .andExpect(jsonPath("default_address.province_name").value("Hà Nội"))
            .andExpect(jsonPath("default_address.district_name").value("Nam Từ Liêm"));
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("default_address.ward_code").value("20907"))
            .andExpect(jsonPath("default_address.street_address").value("12 Mễ Trì"));
        assertThat(users.findById(user.getId()).orElseThrow().getDefaultAddress()).isNotNull();
    }
    @Test void nameOnlyPatchPreservesAddressAndRole() throws Exception {
        update(Map.of("default_address",address(201,1450,"20907"))).andExpect(status().isOk());
        update(Map.of("full_name","New name")).andExpect(status().isOk())
            .andExpect(jsonPath("default_address.ward_code").value("20907"))
            .andExpect(jsonPath("role").value("CUSTOMER"));
    }
    @Test void rejectsDistrictOutsideProvinceWithoutChangingName() throws Exception {
        update(Map.of("full_name","Bad name","default_address",address(202,1450,"20907"))).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getFullName()).isEqualTo("Test");
    }
    @Test void rejectsWardOutsideDistrict() throws Exception {
        update(Map.of("default_address",address(201,1442,"20907"))).andExpect(status().isBadRequest());
    }
    @Test void rejectsIncompleteAddress() throws Exception {
        update(Map.of("default_address",Map.of("province_id",201))).andExpect(status().isBadRequest());
    }
    @Test void explicitlyClearsSavedAddress() throws Exception {
        update(Map.of("default_address",address(201,1450,"20907"))).andExpect(status().isOk());
        update(Map.of("clear_default_address",true)).andExpect(status().isOk());
        assertThat(users.findById(user.getId()).orElseThrow().getDefaultAddress()).isNull();
    }
    @Test void rejectsSavingAndClearingTogether() throws Exception {
        update(Map.of("default_address",address(201,1450,"20907"),"clear_default_address",true)).andExpect(status().isBadRequest());
    }
    @Test void guestCannotSaveAddress() throws Exception {
        mvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("default_address",address(201,1450,"20907"))))).andExpect(status().isUnauthorized());
    }
}
