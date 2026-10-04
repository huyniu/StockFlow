package com.stockflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    // Danh mục cũ không có cha vẫn là nhóm gốc; quan hệ mới không thay ID sản phẩm hoặc lịch sử kho.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

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

    /** Tạo nhóm con dưới một danh mục đã có; chỉ service ADMIN được tạo quan hệ này qua API. */
    public Category(String name, String slug, Category parent) {
        this(name, slug);
        this.parent = parent;
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

    /** Lấy danh mục cha để dựng đường dẫn và kiểm tra giới hạn độ sâu. */
    public Category getParent() {
        return parent;
    }

    /** Đổi tên/slug nhưng giữ ID và vị trí trong cây để sản phẩm, hãng và lịch sử không mất liên kết. */
    public void updateDetails(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }
}
