package com.stockflow.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entity ánh xạ bảng roles, đại diện cho vai trò phân quyền của user trong hệ thống.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * Constructor mặc định cho JPA.
     */
    protected Role() {
    }

    /**
     * Lấy id của role.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy tên role, ví dụ CUSTOMER hoặc ADMIN.
     */
    public String getName() {
        return name;
    }

    /**
     * Lấy thời điểm role được tạo.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
