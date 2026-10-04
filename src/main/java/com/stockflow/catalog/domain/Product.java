package com.stockflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/**
 * Entity sản phẩm, gồm danh mục, thương hiệu tùy chọn, mô tả và bộ ảnh để khách tra cứu.
 */
@Entity
@Table(name = "products")
// Tải SKU theo lô khi dựng nhóm màu; không fetch join collection làm sai COUNT/LIMIT của trang catalog.
@BatchSize(size = 100)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Sản phẩm cũ chưa chọn hãng vẫn hợp lệ; không dò tên để tự gán thương hiệu.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    // Tải ảnh theo lô cho trang catalog; không fetch join collection làm sai phân trang sản phẩm.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", nullable = false, length = 2048)
    @BatchSize(size = 100)
    private List<String> imageUrls = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String description;

    // Thông số là dữ liệu nhập thật; collection có thứ tự và được tải theo lô như thư viện ảnh.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_specifications", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 100)
    private List<ProductSpecification> specifications = new ArrayList<>();

    // Trang model chứa các phiên bản; cấu hình không trực tiếp mang số lượng tồn kho.
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<ProductVersion> versions = new ArrayList<>();

    // Mỗi tổ hợp phiên bản/màu trỏ tới SKU có tồn kho độc lập.
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<ProductVariant> variants = new ArrayList<>();

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
     * Tạo sản phẩm có ảnh đã được validate; constructor cũ vẫn dùng được cho seed và nghiệp vụ hiện có.
     */
    public Product(
            Category category,
            String sku,
            String name,
            BigDecimal unitPrice,
            ProductStatus status,
            String imageUrl) {
        this(category, sku, name, unitPrice, status);
        this.imageUrl = imageUrl;
    }

    /** Tạo nội dung catalog đầy đủ; các constructor cũ vẫn tương thích seed và nghiệp vụ hiện có. */
    public Product(
            Category category,
            String sku,
            String name,
            BigDecimal unitPrice,
            ProductStatus status,
            String imageUrl,
            String description) {
        this(category, sku, name, unitPrice, status, imageUrl);
        this.description = description;
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
     * Đổi hoặc xóa ảnh bìa. Service chỉ gọi khi PATCH có trường ảnh; null ở đây nghĩa là xóa ảnh đã lưu.
     */
    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /** Thay toàn bộ ảnh bổ sung theo thứ tự nhập; danh sách rỗng xóa ảnh bổ sung trong transaction. */
    public void replaceImageUrls(List<String> imageUrls) {
        this.imageUrls.clear();
        this.imageUrls.addAll(imageUrls);
    }

    /** Đổi hoặc xóa mô tả; service chỉ gọi khi PATCH có trường mô tả khác null. */
    public void updateDescription(String description) {
        this.description = description;
    }

    /** Thay bảng thông số trong transaction; mảng rỗng xóa bảng, không để lại dòng thiếu giá trị. */
    public void replaceSpecifications(List<ProductSpecification> specifications) {
        this.specifications.clear();
        this.specifications.addAll(specifications);
    }

    /** Danh sách chỉ đọc giữ thứ tự các thông số đã khai báo. */
    public List<ProductSpecification> getSpecifications() {
        return List.copyOf(specifications);
    }

    /** Các màu thuộc trang chung; SKU con không có nhóm màu lồng nhau. */
    public List<ProductVariant> getVariants() {
        return List.copyOf(variants);
    }

    /** Đồng bộ collection trong cùng transaction khi service vừa tạo một lựa chọn màu. */
    public void addVariant(ProductVariant variant) {
        this.variants.add(variant);
    }

    /** Các cấu hình thuộc cùng thẻ model; bản chưa thêm SKU chưa thể mua được. */
    public List<ProductVersion> getVersions() {
        return List.copyOf(versions);
    }

    /** Đồng bộ collection khi ADMIN thêm phiên bản trong transaction khóa model. */
    public void addVersion(ProductVersion version) {
        versions.add(version);
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

    /** Lấy hãng đã lưu; null nghĩa là ADMIN chưa khai báo thương hiệu. */
    public Brand getBrand() {
        return brand;
    }

    /** Gán hoặc xóa hãng trong transaction của service, giữ nguyên SKU, giá và liên kết tồn kho. */
    public void updateBrand(Brand brand) {
        this.brand = brand;
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

    /** Lấy ảnh bìa đã lưu; null cho phép client chọn ảnh dự phòng. */
    public String getImageUrl() {
        return imageUrl;
    }

    /** Trả bản sao chỉ đọc của ảnh bổ sung; ảnh bìa vẫn được đọc bằng getImageUrl. */
    public List<String> getImageUrls() {
        return List.copyOf(imageUrls);
    }

    /** Lấy mô tả dạng văn bản; client phải hiển thị an toàn, không thực thi HTML trong nội dung. */
    public String getDescription() {
        return description;
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
