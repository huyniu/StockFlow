package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 404 cho trường hợp tài nguyên không tồn tại.
 */
public class ResourceNotFoundException extends AppException {

    /**
     * Tạo lỗi resource not found với thông điệp cụ thể.
     */
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
