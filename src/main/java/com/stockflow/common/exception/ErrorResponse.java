package com.stockflow.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Định dạng lỗi chuẩn cho toàn bộ API để client luôn nhận được cấu trúc nhất quán.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ErrorDetail> errors) {
}
