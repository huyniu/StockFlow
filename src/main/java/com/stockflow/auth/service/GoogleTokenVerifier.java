package com.stockflow.auth.service;

import com.stockflow.common.exception.UnauthorizedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Only Google's pinned JWKS endpoint supplies keys; an ID token never supplies a trusted key URL. */
@Component
public class GoogleTokenVerifier {
    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${app.security.google.client-id:}") String clientId) {
        this.clientId = clientId.trim();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        var googleDecoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .restOperations(new RestTemplate(factory)).build();
        googleDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ofSeconds(30)));
        this.decoder = googleDecoder;
    }

    GoogleTokenVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
    }

    public String clientId() { return clientId; }
    public boolean enabled() { return !clientId.isBlank(); }

    public Jwt verify(String credential, String nonce) {
        if (!enabled()) throw new UnauthorizedException("Đăng nhập Google chưa được cấu hình.");
        try {
            Jwt token = decoder.decode(credential);
            String issuer = token.getClaimAsString("iss");
            String email = token.getClaimAsString("email");
            Instant now = Instant.now();
            if (!Set.of("https://accounts.google.com","accounts.google.com").contains(issuer == null ? "" : issuer)
                || !token.getAudience().contains(clientId)
                || (token.getAudience().size() > 1 && !clientId.equals(token.getClaimAsString("azp")))
                || (token.hasClaim("azp") && !clientId.equals(token.getClaimAsString("azp")))
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now.minusSeconds(30))
                || token.getIssuedAt() == null || token.getIssuedAt().isAfter(now.plusSeconds(30))
                || token.getSubject() == null || token.getSubject().isBlank() || token.getSubject().length() > 255
                || email == null || email.length() > 255 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || !Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))
                || nonce == null || !nonce.equals(token.getClaimAsString("nonce"))) {
                throw new IllegalArgumentException("Invalid Google claims");
            }
            return token;
        } catch (RuntimeException ex) {
            // Never echo credential, claims or Google's internal errors to the client/log.
            throw new UnauthorizedException("Không xác thực được tài khoản Google. Vui lòng thử lại.");
        }
    }
}
