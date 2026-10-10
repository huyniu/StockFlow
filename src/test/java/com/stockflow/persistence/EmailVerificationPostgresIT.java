package com.stockflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.service.EmailService;
import com.stockflow.auth.support.VerificationOtpMail;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;

/** SQL production và khóa PostgreSQL thật; chỉ database QA riêng, không chạm local/Render đang dùng. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("postgres-test")
class EmailVerificationPostgresIT {
    private static final String EXTERNAL_URL=System.getProperty("stockflow.otp-pg-test.url");
    private static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("stockflow_auth_qa").withUsername("stockflow_auth_qa").withPassword("test-only");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService mail;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        String url,username,password;
        if(EXTERNAL_URL==null){POSTGRES.start();url=POSTGRES.getJdbcUrl();username=POSTGRES.getUsername();password=POSTGRES.getPassword();}
        else{
            if(!EXTERNAL_URL.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/stockflow_auth_qa"))
                throw new IllegalArgumentException("Only a dedicated stockflow_auth_qa database on loopback is allowed.");
            url=EXTERNAL_URL;username="stockflow_qa";password="";
        }
        // Chuẩn bị token cũ tại V30 để Spring thật sự nâng cấp V31/V32 khi khởi động.
        var source=new DriverManagerDataSource(url,username,password);
        Flyway.configure().dataSource(source).locations("filesystem:src/main/resources/db/migration").target("30").load().migrate();
        var db=new JdbcTemplate(source);
        db.update("INSERT INTO users(email,password_hash,full_name,role_id,email_verified) SELECT 'legacy-otp@qa.test','unused','Legacy OTP',id,FALSE FROM roles WHERE name='CUSTOMER'");
        db.update("INSERT INTO email_verification_tokens(user_id,otp_code,expires_at) SELECT id,'909090',CURRENT_TIMESTAMP + INTERVAL '15 minutes' FROM users WHERE email='legacy-otp@qa.test'");
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->username);
        properties.add("spring.datasource.password",()->password);properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @AfterAll static void stopContainer(){if(EXTERNAL_URL==null)POSTGRES.stop();}

    @Test void productionMigrationInvalidatesLegacyCodeWithoutVerifyingAccount() throws Exception {
        call("verify-email",Map.of("email","legacy-otp@qa.test","otp","909090")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email='legacy-otp@qa.test'",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='email_verification_tokens' AND column_name='otp_code'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT otp_hash FROM email_verification_tokens t JOIN users u ON u.id=t.user_id WHERE u.email='legacy-otp@qa.test'",String.class)).isEqualTo("legacy-invalidated");
    }
    @Test void postgresCommitsFiveWrongAttemptsAndSerializesEightRequests() throws Exception {
        String email=register(),code=VerificationOtpMail.latest(mail,email),wrong=code.equals("000000")?"000001":"000000";
        assertThat(concurrent(8,()->call("verify-email",Map.of("email",email,"otp",wrong)).andReturn().getResponse().getStatus())).containsOnly(400);
        assertThat(jdbc.queryForObject("SELECT attempts FROM email_verification_tokens t JOIN users u ON u.id=t.user_id WHERE u.email=?",Integer.class,email)).isEqualTo(5);
        call("verify-email",Map.of("email",email,"otp",code)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email=?",Boolean.class,email)).isFalse();
    }
    @Test void postgresAcceptsCorrectCodeExactlyOnceUnderConcurrency() throws Exception {
        String email=register(),code=VerificationOtpMail.latest(mail,email);
        assertThat(concurrent(2,()->call("verify-email",Map.of("email",email,"otp",code)).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,400);
    }
    @Test void postgresSerializesResendAndOnlyLatestCodeWorks() throws Exception {
        String email=register(),old=VerificationOtpMail.latest(mail,email);
        jdbc.update("UPDATE email_verification_tokens SET created_at=CURRENT_TIMESTAMP - INTERVAL '61 seconds' WHERE user_id=(SELECT id FROM users WHERE email=?)",email);
        assertThat(concurrent(2,()->call("resend-otp",Map.of("email",email)).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,429);
        String latest=VerificationOtpMail.latest(mail,email);assertThat(latest).isNotEqualTo(old);
        call("verify-email",Map.of("email",email,"otp",old)).andExpect(status().isBadRequest());
        call("verify-email",Map.of("email",email,"otp",latest)).andExpect(status().isOk());
    }
    private String register() throws Exception {
        String email=UUID.randomUUID()+"@qa.test";
        call("register",Map.of("email",email,"password","Secret@123","full_name","PostgreSQL OTP QA")).andExpect(status().isCreated());return email;
    }
    private ResultActions call(String endpoint,Map<String,String> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/"+endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private List<Integer> concurrent(int count,Callable<Integer> action) throws Exception {
        var pool=Executors.newFixedThreadPool(count);var ready=new CountDownLatch(count);var start=new CountDownLatch(1);
        try{
            var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<count;i++)futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Start timeout");return action.call();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            var results=new ArrayList<Integer>();for(var future:futures)results.add(future.get(30,TimeUnit.SECONDS));return results;
        }finally{pool.shutdownNow();}
    }
}
