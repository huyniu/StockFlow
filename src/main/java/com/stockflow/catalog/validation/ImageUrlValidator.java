package com.stockflow.catalog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Kiểm tra cấu trúc URL bằng URI; backend chỉ lưu đường dẫn và không tải ảnh hay gọi máy chủ bên ngoài.
 */
public class ImageUrlValidator implements ConstraintValidator<ImageUrl, String> {

    /**
     * Chặn scheme nguy hiểm, URL thiếu host, thông tin đăng nhập trong URL và đường dẫn vượt thư mục assets.
     * Null/trống cho phép request cũ không có ảnh và thao tác xóa ảnh bằng chuỗi trống.
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(value.strip());
            if (uri.isAbsolute()) {
                String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
                return ("https".equals(scheme) || "http".equals(scheme))
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null;
            }

            String path = uri.getPath();
            return uri.getRawAuthority() == null
                    && path != null
                    && path.startsWith("/assets/")
                    && !path.contains("\\")
                    && path.chars().noneMatch(Character::isISOControl)
                    && Arrays.stream(path.split("/"))
                            .noneMatch(segment -> ".".equals(segment) || "..".equals(segment));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
