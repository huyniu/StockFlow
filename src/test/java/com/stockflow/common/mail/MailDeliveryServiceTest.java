package com.stockflow.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.stockflow.auth.service.EmailService;
import com.stockflow.common.config.MailProperties;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailDeliveryServiceTest {
    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";
    private MockRestServiceServer server;
    private RestClient http;
    private JavaMailSender smtp;
    private DefaultListableBeanFactory beans;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        http = builder.build();
        smtp = mock(JavaMailSender.class);
        beans = new DefaultListableBeanFactory();
        beans.registerSingleton("smtp", smtp);
    }

    private MailDeliveryService delivery(String provider, String from, String key) {
        return new MailDeliveryService(new MailProperties(provider, from, " StockFlow ", key, null, null),
                beans.getBeanProvider(JavaMailSender.class), http);
    }

    private MailDeliveryService brevo() {
        return delivery(" BREVO ", " sender@example.test ", " test-api-key ");
    }

    @Test
    void brevoUsesHttpsAuthenticationAndPreservesVietnameseContent() {
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("sender@example.test"))
                .andExpect(jsonPath("$.sender.name").value("StockFlow"))
                .andExpect(jsonPath("$.to[0].email").value("customer@example.test"))
                .andExpect(jsonPath("$.to.length()").value(1))
                .andExpect(jsonPath("$.subject").value("Xác thực email"))
                .andExpect(jsonPath("$.textContent").value("Mã OTP: 123456\nHết hạn sau 15 phút."))
                .andRespond(withSuccess("{\"messageId\":\"<accepted@brevo.test>\"}", MediaType.APPLICATION_JSON));
        brevo().send("customer@example.test", "Xác thực email", "Mã OTP: 123456\nHết hạn sau 15 phút.");
        server.verify();
        verifyNoInteractions(smtp);
    }

    @Test
    void allAuthenticationEmailsUseTheSharedDelivery() {
        for (String subject : new String[]{"StockFlow - Xác thực email", "StockFlow - Đặt lại mật khẩu",
                "StockFlow - Mật khẩu đã được thay đổi"}) {
            server.expect(requestTo(ENDPOINT)).andExpect(jsonPath("$.subject").value(subject))
                    .andExpect(jsonPath("$.to[0].email").value("customer@example.test"))
                    .andRespond(withSuccess("{\"messageId\":\"accepted\"}", MediaType.APPLICATION_JSON));
        }
        var auth = new EmailService(brevo());
        auth.sendVerificationOtp("customer@example.test", "123456");
        auth.sendPasswordResetOtp("customer@example.test", "654321");
        auth.sendPasswordChanged("customer@example.test");
        server.verify();
        verifyNoInteractions(smtp);
    }

    @Test
    void smtpStillCallsJavaMailSender() {
        var delivery = delivery("smtp", "sender@example.test", "");
        assertThat(delivery.isConfigured()).isTrue();
        delivery.send("customer@example.test", "Đơn hàng", "Nội dung");
        var capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(capture.capture());
        assertThat(capture.getValue().getFrom()).isEqualTo("sender@example.test");
        assertThat(capture.getValue().getTo()).containsExactly("customer@example.test");
        assertThat(capture.getValue().getSubject()).isEqualTo("Đơn hàng");
        assertThat(capture.getValue().getText()).isEqualTo("Nội dung");
        server.verify();
    }

    @Test
    void smtpWithoutABeanIsUnavailable() {
        beans.destroySingleton("smtp");
        assertThat(delivery("smtp", "", "").isConfigured()).isFalse();
    }

    @Test
    void consoleModeDoesNotSendEmailEvenWithCredentialsPresent() {
        var delivery = delivery("console", "sender@example.test", "test-api-key");
        assertThat(delivery.isConfigured()).isFalse();
        assertThatThrownBy(() -> delivery.send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class);
        verifyNoInteractions(smtp);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "sender"})
    void incompleteBrevoConfigurationNeverFallsBackToSmtp(String missing) {
        var delivery = delivery("brevo", missing.equals("sender") ? " " : "sender@example.test",
                missing.equals("key") ? " " : "test-api-key");
        assertThat(delivery.isConfigured()).isFalse();
        assertThatThrownBy(() -> delivery.send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class);
        verifyNoInteractions(smtp);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void providerErrorsAreFailuresWithoutLeakingResponseOrFallingBack(int status) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .body("private-api-key and private-OTP").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> brevo().send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class).hasMessage("Brevo rejected email (HTTP " + status + ")")
                .hasNoCause();
        server.verify();
        verifyNoInteractions(smtp);
    }

    @Test
    void connectionTimeoutIsReportedAsDeliveryFailure() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new IOException("private-OTP")));
        assertThatThrownBy(() -> brevo().send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class).hasMessage("Brevo connection or response failed").hasNoCause();
        server.verify();
        verifyNoInteractions(smtp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"messageId\":\"\"}", "{\"messageId\":123}"})
    void successWithoutProviderAcknowledgementIsRejected(String response) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> brevo().send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class).hasMessage("Brevo did not acknowledge the email");
        server.verify();
    }

    @Test
    void emptyProviderResponseIsRejected() {
        server.expect(requestTo(ENDPOINT)).andRespond(withNoContent());
        assertThatThrownBy(() -> brevo().send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class);
        server.verify();
    }

    @Test
    void malformedProviderResponseIsRejected() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("not-json private-OTP", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> brevo().send("customer@example.test", "subject", "text"))
                .isInstanceOf(MailSendException.class).hasMessage("Brevo connection or response failed").hasNoCause();
        server.verify();
    }

    @Test
    void invalidProviderFailsConfigurationWithoutExposingKey() {
        assertThatThrownBy(() -> delivery("typo", "sender@example.test", "private-key"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("MAIL_PROVIDER must be smtp, brevo or console");
        assertThat(new MailProperties("brevo", "", "", "private-key", null, null).toString())
                .doesNotContain("private-key");
    }
}
