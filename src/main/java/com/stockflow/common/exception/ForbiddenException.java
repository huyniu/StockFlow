package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 403 cho trường hợp user đã đăng nhập nhưng không có quyền thực hiện thao tác.
 */
public class ForbiddenException extends AppException {

    /**
     * Tạo lỗi forbidden với thông điệp cụ thể.
     */
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
