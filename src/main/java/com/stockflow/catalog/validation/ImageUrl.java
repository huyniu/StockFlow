package com.stockflow.catalog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc ảnh bìa và từng phần tử bộ ảnh: nhận HTTP/HTTPS hoặc /assets/, chặn mã thực thi và tệp máy chủ.
 * Giá trị trống vẫn hợp lệ vì ảnh là tùy chọn; service chuẩn hóa giá trị đó trước khi lưu.
 */
@Constraint(validatedBy = ImageUrlValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ImageUrl {

    /** Thông báo tiếng Việt được đưa vào response lỗi validation thống nhất. */
    String message() default "Ảnh phải là URL HTTP/HTTPS hợp lệ hoặc đường dẫn /assets/…";

    /** Nhóm validation mặc định của Jakarta Bean Validation. */
    Class<?>[] groups() default {};

    /** Metadata cho framework validation, không tham gia nội dung URL. */
    Class<? extends Payload>[] payload() default {};
}
