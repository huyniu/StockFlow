package com.stockflow.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.hibernate.annotations.BatchSize;

/**
 * Phiên bản của một model, ví dụ 40mm GPS hoặc 256 GB; SKU bán thực tế nằm ở các màu bên dưới.
 * Nhãn tự do phù hợp nhiều ngành hàng, không tạo danh mục giả cho từng cấu hình máy.
 */
@Entity
@Table(name = "product_versions")
@BatchSize(size = 100)
public class ProductVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "name_key", nullable = false, length = 160)
    private String nameKey;

    // Giữ phiên bản đã xóa để SKU và đơn cũ vẫn truy vết được cấu hình ban đầu.
    @Column(nullable = false)
    private boolean archived;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_version_specifications", joinColumns = @JoinColumn(name = "version_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 100)
    private List<ProductSpecification> specifications = new ArrayList<>();

    /** Hàm khởi tạo cho Hibernate. */
    protected ProductVersion() {
    }

    /** Tạo cấu hình chưa có hàng; service sẽ liên kết các SKU màu trong transaction riêng. */
    public ProductVersion(Product product, String name) {
        this.product = product;
        rename(name);
    }

    /** Chuẩn hóa khóa tên để chặn hai cấu hình chỉ khác chữ hoa và khoảng trắng bao quanh. */
    public void rename(String name) {
        this.name = name.strip();
        this.nameKey = this.name.toLowerCase(Locale.ROOT);
    }

    /** Thay thông số riêng; mảng rỗng đưa phiên bản về bảng chung của model. */
    public void replaceSpecifications(List<ProductSpecification> values) {
        specifications.clear();
        specifications.addAll(values);
    }

    /** Ghép thông số theo nhãn: giữ thứ tự chung, ghi đè tại chỗ và thêm nhãn mới ở cuối. */
    public List<ProductSpecification> getEffectiveSpecifications() {
        var merged = new LinkedHashMap<String, ProductSpecification>();
        for (ProductSpecification value : product.getSpecifications()) {
            merged.put(value.getName().toLowerCase(Locale.ROOT), value);
        }
        for (ProductSpecification value : specifications) {
            merged.put(value.getName().toLowerCase(Locale.ROOT), value);
        }
        return merged.values().stream()
                .map(value -> new ProductSpecification(value.getName(), value.getValue()))
                .toList();
    }

    /** ID cấu hình, không dùng làm product_id khi nhập kho hoặc đặt hàng. */
    public Long getId() {
        return id;
    }

    /** Model sở hữu cấu hình; dùng cùng khóa gốc khi ADMIN sửa dữ liệu. */
    public Product getProduct() {
        return product;
    }

    /** Nhãn phiên bản hiển thị nguyên dấu tiếng Việt. */
    public String getName() {
        return name;
    }

    /** Khóa chuẩn hóa được database kiểm tra duy nhất trong model. */
    public String getNameKey() {
        return nameKey;
    }

    /** Phiên bản lưu trữ không được thêm màu hoặc đặt mua; ADMIN có thể khôi phục. */
    public boolean isArchived() {
        return archived;
    }

    /** Xóa/khôi phục chỉ thay khả năng bán, giữ nguyên thông số và các SKU con. */
    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    /** Thông số riêng; khác bảng hiệu lực đã ghép với thông số chung. */
    public List<ProductSpecification> getSpecifications() {
        return List.copyOf(specifications);
    }
}
