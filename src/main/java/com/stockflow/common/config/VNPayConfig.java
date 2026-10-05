package com.stockflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VNPayConfig.Properties.class)
public class VNPayConfig {
    @ConfigurationProperties(prefix = "vnpay")
    public record Properties(String payUrl, String tmnCode, String hashSecret, String returnUrl,
                             String version, String command, String storefrontUrl) {
        public Properties {
            tmnCode = tmnCode == null ? "" : tmnCode.trim();
            hashSecret = hashSecret == null ? "" : hashSecret.trim();
        }
    }
}
