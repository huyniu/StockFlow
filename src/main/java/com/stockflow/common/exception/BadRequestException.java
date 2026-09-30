package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 400 cho request sai về dữ liệu đầu vào hoặc không hợp lệ về nghiệp vụ.
 */
public class BadRequestException extends AppException {

    /**
     * Tạo lỗi bad request với thông điệp cụ thể.
     */
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
