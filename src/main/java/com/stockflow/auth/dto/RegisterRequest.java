package com.stockflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO request đăng ký tài khoản mới. DTO này giới hạn các trường client được phép gửi và gắn Bean Validation
 * để chặn dữ liệu không hợp lệ trước khi vào service.
 */
public record RegisterRequest(
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống.")
        @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự.")
        String password,

        @JsonProperty("full_name")
        @NotBlank(message = "Họ tên không được để trống.")
        String fullName) {
}
