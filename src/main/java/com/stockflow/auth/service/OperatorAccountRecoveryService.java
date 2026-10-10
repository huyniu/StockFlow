package com.stockflow.auth.service;

import com.stockflow.auth.security.DemoAccountPolicy;
import com.stockflow.user.domain.User;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-controlled recovery; no HTTP endpoint, no credentials in audit/logs. */
@Service
public class OperatorAccountRecoveryService {
    private static final List<String> EMAILS = List.of(
            "admin@stockflow.com", "manager@stockflow.com", "staff.hn@stockflow.com");
    private static final List<String> ROLES = List.of("ADMIN", "MANAGER", "WAREHOUSE_STAFF");
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public OperatorAccountRecoveryService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.users = users; this.jdbc = jdbc; this.encoder = encoder;
    }

    /** Lock the same three rows in a fixed order, making replay/concurrent runs harmless. */
    @Transactional
    public boolean recover(String requestId, Map<String, String> credentials) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,100}"))
            throw new IllegalArgumentException("Recovery requires a unique request ID (1-100 safe characters).");
        List<User> targets = EMAILS.stream().map(email -> users.findByEmailForVerification(email)
                .orElseThrow(() -> new IllegalStateException("Recovery target is missing; no accounts changed."))).toList();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM operator_recovery_runs WHERE request_id=?", Long.class, requestId) > 0)
            return false;
        for (int i = 0; i < targets.size(); i++) {
            User user = targets.get(i);
            String password = credentials == null ? null : credentials.get(EMAILS.get(i));
            if (!ROLES.get(i).equals(user.getRole().getName()) || !user.isEmailVerified())
                throw new IllegalStateException("Recovery requires the expected roles and already verified emails; no accounts changed.");
            if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                    || password.isBlank() || DemoAccountPolicy.isLegacyPassword(password)
                    || encoder.matches(password, user.getPasswordHash()))
                throw new IllegalArgumentException("Recovery requires new private passwords (12 characters minimum, 72 UTF-8 bytes maximum); no accounts changed.");
        }
        if (credentials.values().stream().distinct().count() != 3)
            throw new IllegalArgumentException("Use three distinct private recovery passwords; no accounts changed.");
        Instant now = Instant.now();
        for (int i = 0; i < targets.size(); i++) {
            User user = targets.get(i);
            user.setPasswordHash(encoder.encode(credentials.get(EMAILS.get(i))));
            user.setStatus(UserStatus.ACTIVE);
            user.markOperatorRecovered(now);
            user.invalidateAccessTokens();
        }
        users.flush();
        jdbc.update("INSERT INTO operator_recovery_runs(request_id) VALUES (?)", requestId);
        return true;
    }
}
