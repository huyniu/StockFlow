package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lớp exception gốc cho lỗi nghiệp vụ, giữ kèm HTTP status để handler có thể trả lời đúng mã lỗi.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Tạo exception nghiệp vụ với HTTP status và thông điệp rõ ràng cho client.
     */
    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Lấy HTTP status tương ứng với lỗi nghiệp vụ.
     */
    public HttpStatus getStatus() {
        return status;
    }
}
