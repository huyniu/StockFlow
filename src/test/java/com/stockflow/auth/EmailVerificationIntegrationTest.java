package com.stockflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.service.EmailService;
import com.stockflow.auth.support.VerificationOtpMail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:email_verification;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
class EmailVerificationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService mail;

    @Test
    void registrationRequiresVerificationAndLoginReturns403() throws Exception {
        String email = register();
        request("login", Map.of("email", email, "password", "Secret@123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.email").value(email));
        assertThat(otp(email)).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email = ?", Boolean.class, email)).isFalse();
    }

    @Test
    void wrongOtpReturns400() throws Exception {
        String email = register();
        String wrong = otp(email).equals("000000") ? "000001" : "000000";
        request("verify-email", Map.of("email", email, "otp", wrong)).andExpect(status().isBadRequest());
    }

    @Test
    void correctOtpReturnsJwtAndEnablesLoginButCannotBeReused() throws Exception {
        String email = register();
        String otp = otp(email);
        var result = request("verify-email", Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "otp", otp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.access_token").isNotEmpty()).andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).path("access_token").asText();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens t JOIN users u ON u.id=t.user_id WHERE u.email=? AND verified_at IS NOT NULL", Integer.class, email)).isEqualTo(1);
        request("login", Map.of("email", email, "password", "Secret@123")).andExpect(status().isOk());
        request("verify-email", Map.of("email", email, "otp", otp)).andExpect(status().isBadRequest());
    }

    @Test
    void expiredOtpReturns400() throws Exception {
        String email = register();
        String code = otp(email);
        jdbc.update("UPDATE email_verification_tokens SET expires_at=DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE user_id=(SELECT id FROM users WHERE email=?)", email);
        request("verify-email", Map.of("email", email, "otp", code)).andExpect(status().isBadRequest());
    }

    @Test
    void resendIsThrottledAndSupersedesOldToken() throws Exception {
        String email = register();
        request("resend-otp", Map.of("email", email)).andExpect(status().isTooManyRequests());
        String oldCode = otp(email);
        jdbc.update("UPDATE email_verification_tokens SET created_at=DATEADD('SECOND', -61, CURRENT_TIMESTAMP) WHERE user_id=(SELECT id FROM users WHERE email=?)", email);
        request("resend-otp", Map.of("email", email)).andExpect(status().isOk());
        request("resend-otp", Map.of("email", email)).andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens t JOIN users u ON u.id=t.user_id WHERE u.email=?", Integer.class, email)).isEqualTo(2);
        request("verify-email", Map.of("email", email, "otp", oldCode)).andExpect(status().isBadRequest());
        request("verify-email", Map.of("email", email, "otp", otp(email))).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordStillReturns401BeforeVerification() throws Exception {
        String email = register();
        request("login", Map.of("email", email, "password", "wrong-password")).andExpect(status().isUnauthorized());
    }

    @Test
    void migrationVerifiesLegacyAccountsButNewRowsDefaultToUnverified() {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:otp_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var before = org.flywaydb.core.Flyway.configure().dataSource(source)
                .locations("filesystem:src/test/resources/db/migration").target("17").load();
        before.migrate();
        var db = new JdbcTemplate(source);
        db.update("INSERT INTO users(email,password_hash,full_name,role_id) SELECT 'legacy@otp.test','hash','Legacy',id FROM roles WHERE name='CUSTOMER'");
        var migration = org.flywaydb.core.Flyway.configure().dataSource(source)
                .locations("filesystem:src/test/resources/db/migration").target("18").cleanDisabled(false).load();
        try {
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(db.queryForObject("SELECT email_verified FROM users WHERE email='legacy@otp.test'", Boolean.class)).isTrue();
            db.update("INSERT INTO users(email,password_hash,full_name,role_id) SELECT 'new@otp.test','hash','New',id FROM roles WHERE name='CUSTOMER'");
            assertThat(db.queryForObject("SELECT email_verified FROM users WHERE email='new@otp.test'", Boolean.class)).isFalse();
            assertThat(migration.migrate().migrationsExecuted).isZero();
        } finally {
            migration.clean();
        }
    }

    @ParameterizedTest
    @CsvSource({"admin@stockflow.com,Admin@123", "manager@stockflow.com,Manager@123",
            "staff.hn@stockflow.com,Staff@123", "customer@stockflow.com,Customer@123"})
    void onlyPublicCustomerDemoCanLogin(String email, String password) throws Exception {
        if (email.equals("customer@stockflow.com")) {
            request("login", Map.of("email", email, "password", password)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty());
        } else {
            request("login", Map.of("email", email, "password", password)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.access_token").doesNotExist());
        }
    }

    private String register() throws Exception {
        String email = UUID.randomUUID() + "@otp.test";
        request("register", Map.of("email", email, "password", "Secret@123", "full_name", "Khách OTP"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.requires_verification").value(true))
                .andExpect(jsonPath("$.access_token").doesNotExist());
        return email;
    }

    private String otp(String email) {
        return VerificationOtpMail.latest(mail, email);
    }

    private ResultActions request(String endpoint, Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
