package com.stockflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity ánh xạ bảng categories, dùng để nhóm sản phẩm theo danh mục nghiệp vụ.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    /**
     * Constructor mặc định cho JPA.
     */
    protected Category() {
    }

    /**
     * Tạo danh mục mới từ dữ liệu đã được validate ở DTO/service.
     */
    public Category(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    /**
     * Lấy id danh mục.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy tên danh mục hiển thị.
     */
    public String getName() {
        return name;
    }

    /**
     * Lấy slug duy nhất dùng cho URL hoặc tìm kiếm thân thiện.
     */
    public String getSlug() {
        return slug;
    }
}
