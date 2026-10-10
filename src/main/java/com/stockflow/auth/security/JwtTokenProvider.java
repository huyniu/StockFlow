package com.stockflow.auth.security;

import com.stockflow.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dịch vụ tạo và kiểm tra JWT access token cho luồng xác thực stateless.
 * Token được ký bằng HMAC SHA để server có thể xác minh tính toàn vẹn mà không cần lưu session.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    /**
     * Khởi tạo secret key HS256 từ cấu hình. Secret cần đủ dài vì JJWT yêu cầu khóa HMAC đạt độ dài tối thiểu
     * để tránh ký token bằng khóa yếu.
     */
    public JwtTokenProvider(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-ms}") long expirationMs) {
        if ("stockflow-demo-secret-for-local-use-only-2026".equals(secret.trim())) {
            throw new IllegalArgumentException("Replace the published demo JWT secret with a private random JWT_SECRET.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Tạo JWT cho người dùng đã đăng nhập. Subject là email để truy vết user, còn claim {@code role}
     * được đưa vào token để hỗ trợ kiểm tra quyền ở các lớp sau này.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);
        // Gắn thời điểm phát hành và hết hạn để token tự vô hiệu sau khoảng thời gian cấu hình.
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().getName())
                .claim("auth_version", user.getAuthVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Đọc username/email từ subject của token sau khi đã xác minh chữ ký.
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean matchesAuthVersion(String token, User user) {
        Number version = extractClaims(token).get("auth_version", Number.class);
        return (version == null ? 0L : version.longValue()) == user.getAuthVersion();
    }

    /**
     * Kiểm tra token có đúng chữ ký, đúng định dạng và chưa hết hạn hay không.
     * Mọi lỗi parse đều được xem là token không hợp lệ để filter có thể trả 401.
     */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Parse claims bằng secret key. Đây là điểm xác minh chữ ký trung tâm trước khi tin tưởng bất kỳ dữ liệu nào trong token.
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
