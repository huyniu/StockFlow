package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 401 cho trường hợp request chưa xác thực hoặc thông tin xác thực không đúng.
 */
public class UnauthorizedException extends AppException {

    /**
     * Tạo lỗi unauthorized với thông điệp cụ thể.
     */
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
