package com.stockflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.auth.service.EmailService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties={"ghn.token=MOCK_TOKEN","spring.datasource.url=jdbc:h2:mem:account_services;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AccountServicesIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired RoleRepository roles; @Autowired JwtTokenProvider jwt; @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc; @MockitoBean EmailService mail;
    User user, other; String token;
    @BeforeEach void setup(){user=makeUser();other=makeUser();token=jwt.generateToken(user);clearInvocations(mail);}
    User makeUser(){var u=new User(UUID.randomUUID()+"@accounts.test",encoder.encode("Before@123"),"Khách",roles.findByName("CUSTOMER").orElseThrow());u.setEmailVerified(true);return users.save(u);}
    Map<String,Object> address(String label,boolean primary){return Map.of("label",label,"recipient_name","Người nhận","recipient_phone","0901234567","province_id",201,"district_id",1450,"ward_code","20907","street_address","12 đường thử nghiệm","is_default",primary);}
    ResultActions call(String method,String path,Object body,String bearer)throws Exception{
        var request=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
        request.header("Authorization","Bearer "+bearer);if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));return mvc.perform(request);
    }
    long create(String label,boolean primary)throws Exception{return json.readTree(call("POST","/api/v1/users/me/addresses",address(label,primary),token).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();}
    @Test void firstAddressAutomaticallyBecomesDefaultAndSyncsLegacyProfile()throws Exception{
        create("Nhà",false);
        call("GET","/api/v1/users/me/addresses",null,token).andExpect(jsonPath("$[0].is_default").value(true)).andExpect(jsonPath("$[0].user_id").doesNotExist());
        call("GET","/api/v1/users/me",null,token).andExpect(jsonPath("default_address.district_id").value(1450)).andExpect(jsonPath("default_address.ward_code").value("20907"));
    }
    @Test void changingDefaultDoesNotDeleteOtherAddresses()throws Exception{
        long first=create("Nhà",false),second=create("Công ty",true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_addresses WHERE user_id=? AND is_default=TRUE",Integer.class,user.getId())).isOne();
        call("POST","/api/v1/users/me/addresses/"+first+"/default",null,token).andExpect(status().isOk());
        call("GET","/api/v1/users/me/addresses",null,token).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(first));
        assertThat(second).isNotEqualTo(first);
    }
    @Test void anotherAccountCannotReadChangeOrDeleteAddress()throws Exception{
        long id=create("Nhà",false);String stranger=jwt.generateToken(other);
        call("PUT","/api/v1/users/me/addresses/"+id,address("Bị sửa",true),stranger).andExpect(status().isNotFound());
        call("DELETE","/api/v1/users/me/addresses/"+id,null,stranger).andExpect(status().isNotFound());
        call("POST","/api/v1/users/me/addresses/"+id+"/default",null,stranger).andExpect(status().isNotFound());
        call("GET","/api/v1/users/me/addresses",null,stranger).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void deletingDefaultPromotesRemainingAndDeletingLastClearsProfile()throws Exception{
        long first=create("Nhà",false),second=create("Công ty",false);
        call("DELETE","/api/v1/users/me/addresses/"+first,null,token).andExpect(status().isNoContent());
        call("GET","/api/v1/users/me/addresses",null,token).andExpect(jsonPath("$[0].id").value(second)).andExpect(jsonPath("$[0].is_default").value(true));
        call("DELETE","/api/v1/users/me/addresses/"+second,null,token).andExpect(status().isNoContent());
        call("GET","/api/v1/users/me",null,token).andExpect(jsonPath("default_address").isEmpty());
    }
    @Test void rejectsMismatchedGhnLocationWithoutInsertingAddress()throws Exception{
        var bad=new HashMap<>(address("Nhà",true));bad.put("ward_code","999999");
        call("POST","/api/v1/users/me/addresses",bad,token).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_addresses WHERE user_id=?",Integer.class,user.getId())).isZero();
    }
    @Test void addressLimitIsEnforced()throws Exception{
        for(int i=0;i<20;i++)create("Địa chỉ "+i,false);
        call("POST","/api/v1/users/me/addresses",address("Địa chỉ 21",false),token).andExpect(status().isBadRequest());
    }
    @Test void passwordChangeRequiresCurrentPasswordAndRejectsSamePassword()throws Exception{
        call("POST","/api/v1/users/me/password",Map.of("current_password","Wrong123","new_password","After@123"),token).andExpect(status().isBadRequest());
        call("POST","/api/v1/users/me/password",Map.of("current_password","Before@123","new_password","Before@123"),token).andExpect(status().isBadRequest());
        call("GET","/api/v1/users/me",null,token).andExpect(status().isOk());verifyNoInteractions(mail);
    }
    @Test void passwordChangeInvalidatesOldJwtAndAllowsNewLogin()throws Exception{
        call("POST","/api/v1/users/me/password",Map.of("current_password","Before@123","new_password","After@123"),token).andExpect(status().isOk());
        call("GET","/api/v1/users/me",null,token).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email",user.getEmail(),"password","After@123")))).andExpect(status().isOk()).andExpect(jsonPath("access_token").isNotEmpty());
        verify(mail).sendPasswordChanged(user.getEmail());
    }
    @Test void rejectsPasswordExceedingBcryptByteLimit()throws Exception{
        call("POST","/api/v1/users/me/password",Map.of("current_password","Before@123","new_password","ệ".repeat(30)),token).andExpect(status().isBadRequest());
        assertThat(encoder.matches("Before@123",users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void accountServicesRequireAuthentication()throws Exception{
        mvc.perform(get("/api/v1/users/me/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
