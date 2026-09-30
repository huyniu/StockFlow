package com.stockflow.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Xử lý exception tập trung để mọi lỗi API trả về cùng một cấu trúc JSON.
 * Cách này giúp client không phải xử lý nhiều định dạng lỗi khác nhau giữa validation, nghiệp vụ và lỗi hệ thống.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Tham số ngày, số hoặc enum sai định dạng phải trả 400 thay vì rơi vào lỗi hệ thống 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleParameterTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(buildResponse(
                status,
                "Tham số " + exception.getName() + " không đúng định dạng.",
                request.getRequestURI(),
                null));
    }

    /**
     * Chuyển custom {@link AppException} thành response với HTTP status đã định nghĩa trong exception.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException exception, HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(buildResponse(status, exception.getMessage(), request.getRequestURI(), null));
    }

    /**
     * Gom các lỗi Bean Validation của {@code @Valid} thành danh sách chi tiết theo field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .toList();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(buildResponse(status, "Dữ liệu request không hợp lệ.", request.getRequestURI(), errors));
    }

    /**
     * Chuyển lỗi bị chặn bởi @PreAuthorize thành 403 thay vì để rơi vào handler 500.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(buildResponse(status, "Bạn không có quyền thực hiện thao tác này.", request.getRequestURI(), null));
    }

    /**
     * Bắt các lỗi chưa được xử lý để tránh lộ stacktrace hoặc chi tiết nội bộ hệ thống ra client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(buildResponse(status, "Hệ thống đang gặp lỗi. Vui lòng thử lại sau.", request.getRequestURI(), null));
    }

    /**
     * Tạo {@link ErrorResponse} đúng format chung của project.
     */
    private ErrorResponse buildResponse(HttpStatus status, String message, String path, List<ErrorDetail> errors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, errors);
    }
}
