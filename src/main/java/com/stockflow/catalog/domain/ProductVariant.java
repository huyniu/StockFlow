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
import java.util.Locale;

/**
 * Gom các SKU phiên bản/màu vào một trang model; tồn kho và đơn vẫn tham chiếu Product hiện có.
 * SKU gốc cũng có một dòng trong bảng này nên màu của nó được kiểm tra trùng như mọi màu khác.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_product_id", nullable = false, unique = true)
    private Product skuProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ProductVersion version;

    @Column(name = "color_name", nullable = false, length = 80)
    private String colorName;

    @Column(name = "color_key", nullable = false, length = 80)
    private String colorKey;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    // Có thể dừng bán màu gốc mà vẫn giữ trang sản phẩm và các màu còn lại hoạt động.
    @Column(nullable = false)
    private boolean enabled = true;

    // Màu đã xóa được giữ riêng với màu tạm ngừng bán, không tháo liên kết SKU/tồn/đơn.
    @Column(nullable = false)
    private boolean archived;

    /** Hàm khởi tạo dành cho Hibernate. */
    protected ProductVariant() {
    }

    /** Liên kết SKU đã lưu; không sinh tồn kho hay movement khi chỉ thêm màu vào catalog. */
    public ProductVariant(
            Product product, Product skuProduct, ProductVersion version, String colorName, String colorHex) {
        this.product = product;
        this.skuProduct = skuProduct;
        this.version = version;
        updateColor(colorName, colorHex);
    }

    /** Chuẩn hóa khóa màu để database chặn hai màu chỉ khác chữ hoa hoặc khoảng trắng. */
    public void updateColor(String colorName, String colorHex) {
        this.colorName = colorName.strip();
        this.colorKey = this.colorName.toLowerCase(Locale.ROOT);
        this.colorHex = colorHex;
    }

    /** Bật/tắt một màu; không xóa SKU đang được đơn hàng và ledger tham chiếu. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            archived = false;
        }
    }

    /** Xóa lựa chọn màu khỏi cửa hàng; lịch sử và SKU thực tế vẫn giữ nguyên. */
    public void archive() {
        archived = true;
        enabled = false;
    }

    /** ADMIN nhận biết màu đã xóa để hiển thị nút khôi phục riêng. */
    public boolean isArchived() {
        return archived;
    }

    /** ID cấu hình màu, khác ID SKU dùng để nhập kho và đặt hàng. */
    public Long getId() {
        return id;
    }

    /** Sản phẩm đại diện cho trang chung trên cửa hàng. */
    public Product getProduct() {
        return product;
    }

    /** SKU thực tế có giá, ảnh và tồn kho riêng. */
    public Product getSkuProduct() {
        return skuProduct;
    }

    /** Phiên bản quyết định các màu hợp lệ và bảng thông số khi khách đổi cấu hình. */
    public ProductVersion getVersion() {
        return version;
    }

    /** Tên màu hiển thị nguyên dấu tiếng Việt. */
    public String getColorName() {
        return colorName;
    }

    /** Khóa duy nhất trong một nhóm sản phẩm. */
    public String getColorKey() {
        return colorKey;
    }

    /** Mã màu dùng cho chấm màu; null khi ADMIN chỉ nhập tên. */
    public String getColorHex() {
        return colorHex;
    }

    /** Trạng thái bán của màu, tách khỏi trạng thái của cả trang sản phẩm. */
    public boolean isEnabled() {
        return enabled;
    }
}
