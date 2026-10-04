package com.stockflow.catalog.dto;

import com.stockflow.catalog.domain.ProductSpecification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO một dòng thông số, dùng chung cho request và response theo thứ tự ADMIN nhập. */
public record ProductSpecificationDto(
        @NotBlank(message = "Tên thông số không được để trống.")
        @Size(max = 100, message = "Tên thông số tối đa 100 ký tự.")
        String name,

        @NotBlank(message = "Giá trị thông số không được để trống.")
        @Size(max = 1000, message = "Giá trị thông số tối đa 1.000 ký tự.")
        String value) {

    /** Chỉ đưa nội dung văn bản ra API, không để lộ entity Hibernate. */
    public static ProductSpecificationDto from(ProductSpecification specification) {
        return new ProductSpecificationDto(specification.getName(), specification.getValue());
    }
}
