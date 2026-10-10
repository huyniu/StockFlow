package com.stockflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.auth.service.EmailService;
import com.stockflow.user.domain.*;
import com.stockflow.user.repository.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password_reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean EmailService mail;
    private static final AtomicInteger ADDRESS = new AtomicInteger();
    private String ip;
    private User user;

    @BeforeEach void setup() {
        ip = "192.0.2." + ADDRESS.incrementAndGet();
        clearInvocations(mail);
        user = new User(UUID.randomUUID()+"@reset.test", encoder.encode("Before@123"), "Khách", roles.findByName("CUSTOMER").orElseThrow());
        user.setEmailVerified(true); user = users.save(user);
    }

    @Test void unknownLockedAndUnverifiedAccountsHaveSamePublicResponse() throws Exception {
        String expected = send(user.getEmail());
        assertThat(send(UUID.randomUUID()+"@reset.test")).isEqualTo(expected);
        user.setStatus(UserStatus.INACTIVE); users.save(user);
        assertThat(send(user.getEmail())).isEqualTo(expected);
        user.setStatus(UserStatus.ACTIVE); user.setEmailVerified(false); users.save(user);
        assertThat(send(user.getEmail())).isEqualTo(expected);
        verify(mail, times(1)).sendPasswordResetOtp(eq(user.getEmail()), anyString());
    }

    @Test void storesOnlyHashOfSixDigitOtp() throws Exception {
        String code = issue();
        assertThat(code).matches("[0-9]{6}");
        String hash = jdbc.queryForObject("SELECT otp_hash FROM password_reset_tokens WHERE user_id=?", String.class, user.getId());
        assertThat(hash).isNotEqualTo(code);
        assertThat(encoder.matches(code, hash)).isTrue();
    }

    @Test void resetChangesPasswordInvalidatesOldJwtAndRequiresFreshLogin() throws Exception {
        String oldJwt = jwt.generateToken(user);
        me(oldJwt).andExpect(status().isOk());
        reset(issue()).andExpect(status().isOk()).andExpect(jsonPath("$.access_token").doesNotExist());
        me(oldJwt).andExpect(status().isUnauthorized());
        call("login", Map.of("email",user.getEmail(),"password","Before@123")).andExpect(status().isUnauthorized());
        var login = call("login", Map.of("email",user.getEmail(),"password","After@123")).andExpect(status().isOk()).andReturn();
        me(json.readTree(login.getResponse().getContentAsString()).path("access_token").asText()).andExpect(status().isOk());
        verify(mail).sendPasswordChanged(user.getEmail());
    }

    @Test void successfulCodeCannotBeReused() throws Exception {
        String code = issue(); reset(code).andExpect(status().isOk()); reset(code).andExpect(status().isBadRequest());
    }

    @Test void wrongAttemptsAreCommittedAndCodeLocksAfterFiveFailures() throws Exception {
        String code = issue(), wrong = code.equals("000000") ? "000001" : "000000";
        for (int i=0;i<5;i++) reset(wrong).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT attempts FROM password_reset_tokens WHERE user_id=?", Integer.class, user.getId())).isEqualTo(5);
        reset(code).andExpect(status().isBadRequest());
        assertThat(encoder.matches("Before@123", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void expiredCodeCannotChangePassword() throws Exception {
        String code = issue();
        jdbc.update("UPDATE password_reset_tokens SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE user_id=?", user.getId());
        reset(code).andExpect(status().isBadRequest());
    }

    @Test void cooldownDoesNotSendMultipleEmailsOrRevealAccountState() throws Exception {
        String response = send(user.getEmail()); assertThat(send(user.getEmail())).isEqualTo(response);
        verify(mail, times(1)).sendPasswordResetOtp(eq(user.getEmail()), anyString());
        assertThat(count()).isEqualTo(1);
    }

    @Test void newestCodeSupersedesOldCode() throws Exception {
        issue();
        jdbc.update("UPDATE password_reset_tokens SET created_at=DATEADD('SECOND',-61,CURRENT_TIMESTAMP),otp_hash=? WHERE user_id=?", encoder.encode("000000"), user.getId());
        send(user.getEmail());
        var codes = ArgumentCaptor.forClass(String.class);
        verify(mail, times(2)).sendPasswordResetOtp(eq(user.getEmail()), codes.capture());
        String latest = codes.getAllValues().get(1);
        // A guaranteed different candidate, regardless of random collisions with previous codes.
        jdbc.update("UPDATE password_reset_tokens SET otp_hash=? WHERE user_id=? AND id=(SELECT MIN(id) FROM password_reset_tokens WHERE user_id=?)",
                encoder.encode(latest.equals("000000") ? "000001" : "000000"), user.getId(), user.getId());
        reset(latest.equals("000000") ? "000001" : "000000").andExpect(status().isBadRequest());
        reset(latest).andExpect(status().isOk());
    }

    @Test void lockedAccountCannotUsePreviouslyIssuedCode() throws Exception {
        String code = issue(); user.setStatus(UserStatus.INACTIVE); users.save(user);
        reset(code).andExpect(status().isBadRequest());
    }

    @Test void limitsEmailIssuanceToFivePerHour() throws Exception {
        for (int i=0;i<5;i++) {
            send(user.getEmail());
            jdbc.update("UPDATE password_reset_tokens SET created_at=DATEADD('SECOND',-61,CURRENT_TIMESTAMP) WHERE user_id=?", user.getId());
        }
        send(user.getEmail()); assertThat(count()).isEqualTo(5);
        verify(mail, times(5)).sendPasswordResetOtp(eq(user.getEmail()), anyString());
    }

    @Test void rejectsMalformedEmailOtpAndShortPassword() throws Exception {
        call("forgot-password", Map.of("email","invalid")).andExpect(status().isBadRequest());
        call("reset-password", Map.of("email",user.getEmail(),"otp","12abc3","new_password","After@123")).andExpect(status().isBadRequest());
        call("reset-password", Map.of("email",user.getEmail(),"otp","123456","new_password","123")).andExpect(status().isBadRequest());
    }

    @Test void rejectsPasswordBeyondBcryptByteLimitWithoutConsumingCode() throws Exception {
        String code = issue();
        call("reset-password", Map.of("email",user.getEmail(),"otp",code,"new_password","á".repeat(40))).andExpect(status().isBadRequest());
        reset(code).andExpect(status().isOk());
    }

    @Test void registrationCodeCannotResetPassword() throws Exception {
        String code = issue();
        jdbc.update("INSERT INTO email_verification_tokens(user_id,otp_hash,expires_at) VALUES(?,?,DATEADD('MINUTE',15,CURRENT_TIMESTAMP))", user.getId(), encoder.encode(code.equals("000000")?"000001":"000000"));
        reset(code.equals("000000")?"000001":"000000").andExpect(status().isBadRequest());
    }

    private String issue() throws Exception {
        send(user.getEmail()); var code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordResetOtp(eq(user.getEmail()), code.capture()); return code.getValue();
    }
    private String send(String email) throws Exception {
        return call("forgot-password", Map.of("email",email)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private ResultActions reset(String code) throws Exception { return call("reset-password", Map.of("email",user.getEmail(),"otp",code,"new_password","After@123")); }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id=?", Integer.class, user.getId()); }
    private ResultActions me(String token) throws Exception { return mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)); }
    private ResultActions call(String endpoint, Map<String,String> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/"+endpoint).with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
