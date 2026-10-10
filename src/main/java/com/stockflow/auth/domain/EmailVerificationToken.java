package com.stockflow.auth.domain;

import com.stockflow.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerificationToken() {}

    public EmailVerificationToken(User user, String otpHash, Instant now) {
        this.user = user;
        this.otpHash = otpHash;
        this.createdAt = now;
        this.expiresAt = now.plusSeconds(15 * 60);
    }

    public String getOtpHash() { return otpHash; }
    public int getAttempts() { return attempts; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void markVerified(Instant now) { verifiedAt = now; }

    public boolean isUsable(Instant now) {
        return verifiedAt == null && invalidatedAt == null && attempts < 5 && expiresAt.isAfter(now);
    }

    /** Gọi trong transaction đã khóa user để verify/resend cùng tài khoản được tuần tự hóa. */
    public void recordWrongAttempt(Instant now) {
        if (attempts < 5) attempts++;
        if (attempts >= 5) invalidatedAt = now;
    }
}
