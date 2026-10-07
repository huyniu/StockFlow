package com.stockflow.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entity ánh xạ bảng users, lưu thông tin đăng nhập và role của người dùng.
 * Mật khẩu trong entity luôn là password hash, không phải mật khẩu gốc.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    public String getGoogleSubject() { return googleSubject; }
    public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;
    public long getAuthVersion() { return authVersion; }
    public void invalidateAccessTokens() { authVersion++; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    // Số liên hệ tùy chọn; thông tin người nhận trên đơn vẫn là bản chụp độc lập.
    @Column(length = 30)
    private String phone;
    @jakarta.persistence.Embedded
    private DefaultAddress defaultAddress;

    public DefaultAddress getDefaultAddress() { return defaultAddress; }

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Constructor mặc định cho JPA.
     */
    protected User() {
    }

    /**
     * Tạo user mới sau khi mật khẩu đã được hash và role đã được xác định.
     */
    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Gán timestamp trước khi insert để entity khớp với cột NOT NULL trong schema.
     */
    @PrePersist
    public void beforeCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        // Nếu caller không truyền trạng thái, tài khoản mới mặc định ở trạng thái ACTIVE như constraint database.
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /**
     * Cập nhật timestamp khi entity thay đổi.
     */
    @PreUpdate
    public void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Lấy id user.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy email đăng nhập.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Lấy password hash để kiểm tra credential, không bao giờ expose ra response.
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Lấy họ tên hiển thị của user.
     */
    public String getFullName() {
        return fullName;
    }

    /** Lấy số điện thoại liên hệ đã chuẩn hóa; tài khoản chưa bổ sung trả null. */
    public String getPhone() {
        return phone;
    }

    /**
     * Lấy role hiện tại của user.
     */
    public Role getRole() {
        return role;
    }

    public void setRole(Role role) { this.role = java.util.Objects.requireNonNull(role); }

    /**
     * Lấy trạng thái tài khoản.
     */
    public UserStatus getStatus() {
        return status;
    }

    /** Trạng thái do service quản trị kiểm tra quyền trước khi thay đổi. */
    public void setStatus(UserStatus status) {
        this.status = java.util.Objects.requireNonNull(status);
    }

    /**
     * Lấy thời điểm tạo tài khoản.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
