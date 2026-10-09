package com.stockflow.common.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockflow.common.config.MailProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Shared delivery for authentication emails and the durable order notification outbox. */
@Service
public class MailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);
    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";
    private final MailProperties properties;
    private final ObjectProvider<JavaMailSender> smtp;
    private final RestClient http;

    public MailDeliveryService(MailProperties properties, ObjectProvider<JavaMailSender> smtp,
                               @Qualifier("mailRestClient") RestClient http) {
        this.properties = properties;
        this.smtp = smtp;
        this.http = http;
        log.info("[MAIL] Provider: {}", properties.provider());
        if ("brevo".equals(properties.provider()) && !isConfigured()) {
            log.warn("[MAIL] Brevo requires BREVO_API_KEY and a verified sender in APP_MAIL_FROM");
        }
    }

    public boolean isConfigured() {
        return switch (properties.provider()) {
            case "brevo" -> !properties.brevoApiKey().isBlank() && !properties.from().isBlank();
            case "smtp" -> smtp.getIfAvailable() != null;
            default -> false;
        };
    }

    public void send(String recipient, String subject, String text) {
        if (!isConfigured()) {
            throw new MailSendException("Email delivery is not configured");
        }
        if ("brevo".equals(properties.provider())) {
            sendViaBrevo(recipient, subject, text);
            return;
        }
        var message = new SimpleMailMessage();
        message.setFrom(properties.from().isBlank() ? "no-reply@stockflow.com" : properties.from());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        smtp.getObject().send(message);
    }

    private void sendViaBrevo(String recipient, String subject, String text) {
        var payload = Map.of(
                "sender", Map.of("email", properties.from(), "name", properties.senderName()),
                "to", List.of(Map.of("email", recipient)),
                "subject", subject,
                "textContent", text);
        try {
            JsonNode response = http.post().uri(BREVO_SEND_URL)
                    .header("api-key", properties.brevoApiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(JsonNode.class);
            if (response == null || !response.path("messageId").isTextual()
                    || response.path("messageId").asText().isBlank()) {
                throw new MailSendException("Brevo did not acknowledge the email");
            }
        } catch (RestClientResponseException exception) {
            // Provider bodies can contain recipients/content; never propagate them into application logs.
            log.warn("[MAIL] Brevo rejected email: HTTP {}", exception.getStatusCode().value());
            throw new MailSendException("Brevo rejected email (HTTP " + exception.getStatusCode().value() + ")");
        } catch (RestClientException exception) {
            log.warn("[MAIL] Brevo connection or response failed");
            throw new MailSendException("Brevo connection or response failed");
        }
    }
}
