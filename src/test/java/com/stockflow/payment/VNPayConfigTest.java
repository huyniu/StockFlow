package com.stockflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockflow.common.config.VNPayConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class VNPayConfigTest {
    @Test
    void environmentVariablesOverrideYamlDefaultsIncludingDemoProfile() {
        for (String profiles : new String[] {"test", "test,demo"}) {
            new ApplicationContextRunner()
                    .withPropertyValues("spring.profiles.active=" + profiles)
                    .withInitializer(context -> {
                        context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                                "vnpay-test-environment", Map.of(
                                "VNPAY_TMN_CODE", "  ENVTMN01 \t",
                                "VNPAY_HASH_SECRET", "\r\n environment-only-test-secret \t")));
                        new ConfigDataApplicationContextInitializer().initialize(context);
                    })
                    .withUserConfiguration(VNPayConfig.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var properties = context.getBean(VNPayConfig.Properties.class);
                        assertThat(properties.tmnCode()).isEqualTo("ENVTMN01");
                        assertThat(properties.hashSecret()).isEqualTo("environment-only-test-secret");
                        assertThat(properties.version()).isEqualTo("2.1.0");
                    });
        }
    }
}
