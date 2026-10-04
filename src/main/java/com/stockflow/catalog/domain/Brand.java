package com.stockflow.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * Thương hiệu độc lập với danh mục: cùng một hãng có thể bán điện thoại, laptop hoặc phụ kiện.
 * Danh mục liên kết dùng để gợi ý trong menu; sản phẩm vẫn lưu hãng bằng khóa ngoại thật.
 */
@Entity
@Table(name = "brands")
@BatchSize(size = 100)
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    // Logo là đường dẫn tùy chọn; hãng cũ chưa nhập ảnh vẫn hiển thị bằng tên.
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "brand_categories",
            joinColumns = @JoinColumn(name = "brand_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @BatchSize(size = 100)
    private Set<Category> categories = new LinkedHashSet<>();

    /** Constructor dành cho JPA; không cho client tự gán ID. */
    protected Brand() {
    }

    /** Tạo hãng và các danh mục gợi ý từ dữ liệu đã kiểm tra tại service. */
    public Brand(String name, String slug, Set<Category> categories) {
        this(name, slug, categories, null);
    }

    /** Tạo hãng có logo đã kiểm tra; giữ constructor cũ cho fixture và các luồng chưa nhập ảnh. */
    public Brand(String name, String slug, Set<Category> categories, String logoUrl) {
        this.name = name;
        this.slug = slug;
        this.categories.addAll(categories);
        this.logoUrl = logoUrl;
    }

    /** Lấy khóa chính của hãng. */
    public Long getId() {
        return id;
    }

    /** Tên dùng trong form sản phẩm và menu công khai. */
    public String getName() {
        return name;
    }

    /** Slug ổn định, không suy đoán hãng từ tên sản phẩm. */
    public String getSlug() {
        return slug;
    }

    /** Đường dẫn logo để storefront và dashboard nhận diện hãng. */
    public String getLogoUrl() {
        return logoUrl;
    }

    /** Chỉ thay logo, không đổi ID/tên/slug hoặc quan hệ sản phẩm và danh mục. */
    public void updateLogo(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    /** Trả bản sao danh mục gợi ý để client không sửa quan hệ ngoài service. */
    public Set<Category> getCategories() {
        return Set.copyOf(categories);
    }
}
