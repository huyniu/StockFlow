package com.stockflow.auth;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.service.GoogleTokenVerifier;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.user.domain.*;
import com.stockflow.user.repository.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:stockflow_google_auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class GoogleAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ObjectMapper json;
    @MockitoBean GoogleTokenVerifier verifier;
    String subject,email,nonce;
    Cookie cookie;
    @BeforeEach void setup() throws Exception {
        subject=UUID.randomUUID().toString(); email=UUID.randomUUID()+"@gmail.com";
        when(verifier.enabled()).thenReturn(true); when(verifier.clientId()).thenReturn("test.apps.googleusercontent.com");
        challenge();
    }
    void challenge() throws Exception {
        var result=mvc.perform(get("/api/v1/auth/google/config")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Cross-Origin-Opener-Policy","same-origin-allow-popups")).andReturn();
        nonce=json.readTree(result.getResponse().getContentAsString()).get("nonce").asText();
        cookie=new Cookie("stockflow_google_nonce",nonce);
    }
    Jwt identity(String email,String subject) {
        return Jwt.withTokenValue("signed-google-token").header("alg","RS256").subject(subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
            .claim("email",email).claim("email_verified",true).claim("name","Google Customer").claim("nonce",nonce).build();
    }
    ResultActions login() throws Exception {
        when(verifier.verify("valid-credential",nonce)).thenReturn(identity(email,subject));
        return mvc.perform(post("/api/v1/auth/google").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"valid-credential\"}"));
    }
    User existing(String role,boolean verified) {
        User user=verifiedUser(email,"original-hash","Existing user",roles.findByName(role).orElseThrow());
        user.setEmailVerified(verified); return users.save(user);
    }
    @Test void createsVerifiedCustomerAndUsableStockflowJwt() throws Exception {
        var result=login().andExpect(status().isOk()).andExpect(jsonPath("user.role").value("CUSTOMER")).andExpect(jsonPath("access_token").isNotEmpty()).andExpect(jsonPath("user.password_hash").doesNotExist()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("email").value(email));
        User user=users.findByEmail(email).orElseThrow(); assertThat(user.isEmailVerified()).isTrue(); assertThat(user.getGoogleSubject()).isEqualTo(subject);
    }
    @Test void loginPageIsPublicAndCanBeReloadedWithReturnUrl() throws Exception {
        mvc.perform(get("/login").param("pre_uri","/san-pham/1")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        assertThat(users.findByEmail(email)).isEmpty();
    }
    @Test void registrationPageIsPublicAndCanBeReloaded() throws Exception {
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        assertThat(users.findByEmail(email)).isEmpty();
    }
    @Test void repeatedGoogleLoginReusesAccountAndSubjectSurvivesGoogleEmailChange() throws Exception {
        login().andExpect(status().isOk()); Long id=users.findByEmail(email).orElseThrow().getId();
        challenge(); email=UUID.randomUUID()+"@gmail.com";
        login().andExpect(status().isOk()).andExpect(jsonPath("user.id").value(id));
        assertThat(users.findByEmail(email)).isEmpty();
    }
    @Test void verifiedGmailAccountKeepsRolePasswordAndProfile() throws Exception {
        User old=existing("MANAGER",true);
        login().andExpect(status().isOk()).andExpect(jsonPath("user.id").value(old.getId())).andExpect(jsonPath("user.role").value("MANAGER")).andExpect(jsonPath("user.full_name").value("Existing user"));
        assertThat(users.findById(old.getId()).orElseThrow().getPasswordHash()).isEqualTo("original-hash");
    }
    @Test void lockedUserCannotLogInOrBecomeUnlocked() throws Exception {
        User old=existing("CUSTOMER",true); old.setStatus(UserStatus.INACTIVE); users.save(old);
        login().andExpect(status().isUnauthorized()).andExpect(jsonPath("access_token").doesNotExist());
        assertThat(users.findById(old.getId()).orElseThrow().getGoogleSubject()).isNull();
    }
    @Test void doesNotLinkPendingRegistration() throws Exception {
        User old=existing("CUSTOMER",false); login().andExpect(status().isConflict());
        assertThat(users.findById(old.getId()).orElseThrow().isEmailVerified()).isFalse();
    }
    @Test void doesNotLinkThirdPartyEmailByEmailAlone() throws Exception {
        email=UUID.randomUUID()+"@example.com"; User old=existing("ADMIN",true);
        login().andExpect(status().isConflict()); assertThat(users.findById(old.getId()).orElseThrow().getGoogleSubject()).isNull();
    }
    @Test void googleCannotReenablePublicOperatorDemo() throws Exception {
        email="admin@stockflow.com";User demo=existing("ADMIN",true);demo.setGoogleSubject(subject);users.saveAndFlush(demo);
        login().andExpect(status().isUnauthorized()).andExpect(jsonPath("access_token").doesNotExist());
    }
    @Test void nonceCannotBeReplayed() throws Exception {
        login().andExpect(status().isOk()); login().andExpect(status().isUnauthorized());
    }
    @Test void requiresBrowserCookieAndRejectsBadCredential() throws Exception {
        when(verifier.verify(anyString(),isNull())).thenThrow(new UnauthorizedException("Invalid nonce"));
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"token\"}")).andExpect(status().isUnauthorized());
        when(verifier.verify("forged",nonce)).thenThrow(new UnauthorizedException("Invalid token"));
        mvc.perform(post("/api/v1/auth/google").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"forged\"}")).andExpect(status().isUnauthorized());
        assertThat(users.findByEmail(email)).isEmpty();
    }
    @Test void validatesCredentialAndReportsDisabledConfiguration() throws Exception {
        mvc.perform(post("/api/v1/auth/google").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"\"}")).andExpect(status().isBadRequest());
        when(verifier.enabled()).thenReturn(false);
        mvc.perform(get("/api/v1/auth/google/config")).andExpect(status().isOk()).andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("client_id").value(""));
    }
}
