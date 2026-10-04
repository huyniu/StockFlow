package com.stockflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Một dòng thông số do ADMIN khai báo; không suy đoán cấu hình từ tên sản phẩm. */
@Embeddable
public class ProductSpecification {

    @Column(name = "specification_name", nullable = false, length = 100)
    private String name;

    @Column(name = "specification_value", nullable = false, length = 1000)
    private String value;

    /** Hàm khởi tạo dành cho JPA. */
    protected ProductSpecification() {
    }

    /** Lưu nhãn và giá trị đã được service chuẩn hóa, giữ thứ tự bằng collection của sản phẩm. */
    public ProductSpecification(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /** Nhãn hiển thị, ví dụ Chipset hoặc Kích thước màn hình. */
    public String getName() {
        return name;
    }

    /** Giá trị văn bản; giao diện phải escape trước khi hiển thị. */
    public String getValue() {
        return value;
    }
}
