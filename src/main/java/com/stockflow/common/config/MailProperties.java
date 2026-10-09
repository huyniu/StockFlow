package com.stockflow.common.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String provider, String from, String senderName, String brevoApiKey,
                             Duration connectTimeout, Duration readTimeout) {
    public MailProperties {
        provider = provider == null ? "smtp" : provider.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("smtp", "brevo", "console").contains(provider)) {
            throw new IllegalArgumentException("MAIL_PROVIDER must be smtp, brevo or console");
        }
        from = from == null ? "" : from.trim();
        senderName = senderName == null || senderName.isBlank() ? "StockFlow" : senderName.trim();
        brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Mail HTTP timeouts must be positive");
        }
    }

    // Do not expose credentials if configuration objects are logged by diagnostic code.
    @Override
    public String toString() {
        return "MailProperties[provider=" + provider + ", brevoApiKey=REDACTED]";
    }
}
