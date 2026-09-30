package com.stockflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO request đăng nhập bằng email và mật khẩu. Validation được đặt tại DTO để controller trả lỗi 400 nhất quán
 * khi thiếu credential hoặc email sai định dạng.
 */
public record LoginRequest(
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống.")
        String password) {
}
