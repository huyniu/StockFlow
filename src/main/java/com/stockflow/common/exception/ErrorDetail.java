package com.stockflow.common.exception;

/**
 * Mô tả một lỗi chi tiết trên từng field khi request vi phạm validation.
 */
public record ErrorDetail(String field, String message, Object rejectedValue) {
}
