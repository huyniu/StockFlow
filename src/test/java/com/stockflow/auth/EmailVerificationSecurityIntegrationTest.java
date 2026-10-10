package com.stockflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.service.EmailService;
import com.stockflow.auth.support.VerificationOtpMail;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Không có transaction bao ngoài: HTTP 400 phải commit số lần sai thật trong database. */
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:otp_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=15000")
@AutoConfigureMockMvc @ActiveProfiles("test")
class EmailVerificationSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailService mail;
    String email, ip;

    @BeforeEach void setup() {
        email=UUID.randomUUID()+"@security.test";
        int value=UUID.randomUUID().hashCode();
        ip="10.20."+((value>>>8)&255)+"."+(value&255);
    }

    @Test void storesOnlyHashAndKeepsFifteenMinuteExpiry() throws Exception {
        register();
        String hash=jdbc.queryForObject("SELECT otp_hash FROM email_verification_tokens WHERE user_id=(SELECT id FROM users WHERE email=?)",String.class,email);
        assertThat(hash).isNotEqualTo(otp());
        assertThat(encoder.matches(otp(),hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT DATEDIFF('SECOND',created_at,expires_at) FROM email_verification_tokens WHERE user_id=(SELECT id FROM users WHERE email=?)",Integer.class,email)).isEqualTo(900);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='email_verification_tokens' AND column_name='otp_code'",Integer.class)).isZero();
    }

    @Test void fifthWrongAttemptCommitsAndPermanentlyInvalidatesThatCode() throws Exception {
        register();String correct=otp();String wrong=wrong(correct);
        for(int i=1;i<=5;i++){
            verify(wrong).andExpect(status().isBadRequest());
            assertThat(attempts()).isEqualTo(i);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id=(SELECT id FROM users WHERE email=?) AND invalidated_at IS NOT NULL",Integer.class,email)).isEqualTo(1);
        verify(correct).andExpect(status().isBadRequest());assertThat(attempts()).isEqualTo(5);
        call("login",Map.of("email",email,"password","Secret@123"),ip).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email=?",Boolean.class,email)).isFalse();
    }

    @Test void correctCodeStillWorksBeforeLimitAndCannotBeReplayed() throws Exception {
        register();verify(wrong(otp())).andExpect(status().isBadRequest());
        verify(otp()).andExpect(status().isOk());assertThat(attempts()).isEqualTo(1);
        verify(otp()).andExpect(status().isBadRequest());
    }

    @Test void simultaneousWrongRequestsCannotExceedFiveOrLeaveCodeUsable() throws Exception {
        register();String correct=otp(),wrong=wrong(correct);
        var statuses=concurrent(8,()->verify(wrong).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(400);assertThat(attempts()).isEqualTo(5);
        verify(correct).andExpect(status().isBadRequest());
    }

    @Test void simultaneousCorrectRequestsIssueExactlyOneLogin() throws Exception {
        register();String code=otp();
        assertThat(concurrent(2,()->verify(code).andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200,400);
    }

    @Test void simultaneousResendSendsOnceAndOnlyNewestCodeWorks() throws Exception {
        register();String oldCode=otp();ageLatest();
        assertThat(concurrent(2,()->resend().andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200,429);
        String newCode=otp();assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(tokenCount()).isEqualTo(2);
        verify(oldCode).andExpect(status().isBadRequest());verify(newCode).andExpect(status().isOk());
    }

    @Test void resendCanReplaceLockedCodeWithoutVerifyingUserAndHasHourlyLimit() throws Exception {
        register();String oldCode=otp();
        for(int i=0;i<5;i++)verify(wrong(oldCode)).andExpect(status().isBadRequest());
        ageLatest();resend().andExpect(status().isOk());
        assertThat(attempts()).isZero();
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email=?",Boolean.class,email)).isFalse();
        for(int i=0;i<3;i++){ageLatest();resend().andExpect(status().isOk());}
        ageLatest();resend().andExpect(status().isTooManyRequests());assertThat(tokenCount()).isEqualTo(5);
        verify(otp()).andExpect(status().isOk());
    }

    @Test void verifyIpLimitCannotBeBypassedBySpoofedForwardedHeaders() throws Exception {
        for(int i=0;i<30;i++){
            mvc.perform(post("/api/v1/auth/verify-email").with(r->{r.setRemoteAddr(ip);return r;})
                    .header("X-Forwarded-For","198.51.100."+i).header("Forwarded","for=198.51.100."+i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email",UUID.randomUUID()+"@security.test","otp","123456"))))
                    .andExpect(status().isBadRequest());
        }
        call("verify-email",Map.of("email",email,"otp","123456"),ip).andExpect(status().isTooManyRequests());
    }

    @Test void verifyEmailLimitNormalizesCaseAndSurvivesChangingIp() throws Exception {
        for(int i=0;i<10;i++)call("verify-email",Map.of("email",i%2==0?email:email.toUpperCase(Locale.ROOT),"otp","123456"),"192.0.2."+i).andExpect(status().isBadRequest());
        call("verify-email",Map.of("email",email,"otp","123456"),"192.0.2.20").andExpect(status().isTooManyRequests());
    }

    @Test void resendIpLimitWorksEvenForUnknownEmails() throws Exception {
        for(int i=0;i<10;i++)call("resend-otp",Map.of("email",UUID.randomUUID()+"@security.test"),ip).andExpect(status().isBadRequest());
        resend().andExpect(status().isTooManyRequests());assertThat(tokenCount()).isZero();
    }

    @Test void expiredCodeIsRejectedWithoutConsumingAttempts() throws Exception {
        register();jdbc.update("UPDATE email_verification_tokens SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE user_id=(SELECT id FROM users WHERE email=?)",email);
        verify(otp()).andExpect(status().isBadRequest());assertThat(attempts()).isZero();
    }

    private void register() throws Exception { call("register",Map.of("email",email,"password","Secret@123","full_name","Kiểm thử OTP"),ip).andExpect(status().isCreated()); }
    private String otp(){return VerificationOtpMail.latest(mail,email);}
    private String wrong(String code){return code.equals("000000")?"000001":"000000";}
    private ResultActions verify(String code) throws Exception {return call("verify-email",Map.of("email",email,"otp",code),ip);}
    private ResultActions resend() throws Exception {return call("resend-otp",Map.of("email",email),ip);}
    private ResultActions call(String endpoint,Map<String,String> body,String address) throws Exception {
        return mvc.perform(post("/api/v1/auth/"+endpoint).with(r->{r.setRemoteAddr(address);return r;})
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private int attempts(){return jdbc.queryForObject("SELECT attempts FROM email_verification_tokens WHERE user_id=(SELECT id FROM users WHERE email=?) ORDER BY created_at DESC,id DESC LIMIT 1",Integer.class,email);}
    private int tokenCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id=(SELECT id FROM users WHERE email=?)",Integer.class,email);}
    private void ageLatest(){jdbc.update("UPDATE email_verification_tokens SET created_at=DATEADD('SECOND',-61,CURRENT_TIMESTAMP) WHERE id=(SELECT id FROM email_verification_tokens WHERE user_id=(SELECT id FROM users WHERE email=?) ORDER BY created_at DESC,id DESC LIMIT 1)",email);}
    private List<Integer> concurrent(int count,Callable<Integer> task) throws Exception {
        var pool=Executors.newFixedThreadPool(count);
        try{
            var ready=new CountDownLatch(count);var go=new CountDownLatch(1);
            var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<count;i++)futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError("Start timeout");return task.call();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            var results=new ArrayList<Integer>();for(var future:futures)results.add(future.get(30,TimeUnit.SECONDS));return results;
        } finally { pool.shutdownNow(); }
    }
}
