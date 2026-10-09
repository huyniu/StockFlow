package com.stockflow.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class MailConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(MailConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void environmentVariablesBindToHttpsMailConfiguration() {
        context.withPropertyValues("MAIL_PROVIDER=brevo", "BREVO_API_KEY= test-key ",
                "APP_MAIL_FROM= sender@example.test ", "MAIL_SENDER_NAME=StockFlow Shop")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var mail = application.getBean(MailProperties.class);
                    assertThat(mail.provider()).isEqualTo("brevo");
                    assertThat(mail.brevoApiKey()).isEqualTo("test-key");
                    assertThat(mail.from()).isEqualTo("sender@example.test");
                    assertThat(mail.senderName()).isEqualTo("StockFlow Shop");
                    assertThat(mail.connectTimeout()).hasSeconds(5);
                    assertThat(mail.readTimeout()).hasSeconds(10);
                    assertThat(application).hasBean("mailRestClient");
                });
    }

    @Test
    void testProfileIgnoresExternalEmailKeys() {
        context.withPropertyValues("spring.profiles.active=test", "MAIL_PROVIDER=brevo",
                "BREVO_API_KEY=should-not-be-used", "APP_MAIL_FROM=sender@example.test")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var mail = application.getBean(MailProperties.class);
                    assertThat(mail.provider()).isEqualTo("smtp");
                    assertThat(mail.brevoApiKey()).isEmpty();
                });
    }

    @Test
    void postgresTestProfileIgnoresExternalEmailKeys() {
        context.withPropertyValues("spring.profiles.active=postgres-test", "MAIL_PROVIDER=brevo",
                "BREVO_API_KEY=should-not-be-used", "APP_MAIL_FROM=sender@example.test")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application.getBean(MailProperties.class).provider()).isEqualTo("smtp");
                    assertThat(application.getBean(MailProperties.class).brevoApiKey()).isEmpty();
                });
    }
}
