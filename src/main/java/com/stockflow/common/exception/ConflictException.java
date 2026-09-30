package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 409 cho trường hợp request xung đột với trạng thái dữ liệu hiện tại, ví dụ email đã tồn tại.
 */
public class ConflictException extends AppException {

    /**
     * Tạo lỗi conflict với thông điệp cụ thể.
     */
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
