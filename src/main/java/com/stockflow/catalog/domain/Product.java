package com.stockflow.catalog.domain;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity ánh xạ bảng products, lưu dữ liệu catalog cốt lõi như SKU, tên, giá và trạng thái bán.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Constructor mặc định cho JPA.
     */
    protected Product() {
    }

    /**
     * Tạo sản phẩm mới với danh mục đã tồn tại và dữ liệu đã được validate.
     */
    public Product(Category category, String sku, String name, BigDecimal unitPrice, ProductStatus status) {
        this.category = category;
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.status = status;
    }

    /**
     * Gán thời điểm tạo trước khi insert để entity khớp schema có cột created_at NOT NULL.
     */
    @PrePersist
    public void beforeCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    /**
     * Cập nhật các trường được phép sửa trong Milestone 2: tên, giá và trạng thái.
     */
    public void update(String name, BigDecimal unitPrice, ProductStatus status) {
        if (name != null) {
            this.name = name;
        }
        if (unitPrice != null) {
            this.unitPrice = unitPrice;
        }
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * Lấy id sản phẩm.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy danh mục của sản phẩm.
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Lấy mã SKU duy nhất của sản phẩm.
     */
    public String getSku() {
        return sku;
    }

    /**
     * Lấy tên sản phẩm hiển thị.
     */
    public String getName() {
        return name;
    }

    /**
     * Lấy giá bán đơn vị của sản phẩm.
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * Lấy trạng thái sản phẩm.
     */
    public ProductStatus getStatus() {
        return status;
    }

    /**
     * Lấy thời điểm tạo sản phẩm.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
