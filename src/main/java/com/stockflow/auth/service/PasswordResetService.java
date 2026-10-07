package com.stockflow.auth.service;

import com.stockflow.common.exception.BadRequestException;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PasswordResetService {
    public static final String SENT_MESSAGE = "Nếu email thuộc tài khoản đang hoạt động và đã xác thực, mã đặt lại mật khẩu sẽ được gửi. Vui lòng kiểm tra hộp thư hoặc thử lại sau 60 giây.";
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder encoder, EmailService email) {
        this.users = users; this.jdbc = jdbc; this.encoder = encoder; this.email = email;
    }

    @Transactional
    public void requestReset(String address) {
        // Hash for every request, including unknown accounts; never expose account existence.
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = encoder.encode(otp);
        var user = users.findByEmailForVerification(normalize(address)).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) return;
        Instant now = Instant.now();
        int recent = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id=? AND created_at>?",
                Integer.class, user.getId(), Timestamp.from(now.minusSeconds(60)));
        int hourly = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id=? AND created_at>?",
                Integer.class, user.getId(), Timestamp.from(now.minusSeconds(3600)));
        if (recent > 0 || hourly >= 5) return;
        jdbc.update("INSERT INTO password_reset_tokens(user_id,otp_hash,expires_at,created_at) VALUES (?,?,?,?)",
                user.getId(), hash, Timestamp.from(now.plusSeconds(900)), Timestamp.from(now));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { email.sendPasswordResetOtp(user.getEmail(), otp); }
        });
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void reset(String address, String otp, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BadRequestException("Mật khẩu quá dài; vui lòng dùng tối đa 72 byte.");
        var user = users.findByEmailForVerification(normalize(address)).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) throw invalid();
        var rows = jdbc.queryForList("SELECT id,otp_hash,attempts,expires_at,used_at FROM password_reset_tokens WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 1", user.getId());
        if (rows.isEmpty()) throw invalid();
        var token = rows.get(0);
        Instant now = Instant.now();
        if (token.get("used_at") != null || ((Number)token.get("attempts")).intValue() >= 5
                || !toOffset(token.get("expires_at")).toInstant().isAfter(now)) throw invalid();
        if (!encoder.matches(otp, (String)token.get("otp_hash"))) {
            jdbc.update("UPDATE password_reset_tokens SET attempts=attempts+1 WHERE id=?", token.get("id"));
            throw invalid();
        }
        user.setPasswordHash(encoder.encode(password));
        user.invalidateAccessTokens();
        jdbc.update("UPDATE password_reset_tokens SET used_at=? WHERE user_id=? AND used_at IS NULL", Timestamp.from(now), user.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { email.sendPasswordChanged(user.getEmail()); }
        });
    }

    private java.time.OffsetDateTime toOffset(Object value) {
        if (value instanceof java.time.OffsetDateTime date) return date;
        return ((Timestamp)value).toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    private BadRequestException invalid() { return new BadRequestException("Mã đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã nhập sai quá 5 lần."); }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
