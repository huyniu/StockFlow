package com.stockflow.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Hồ sơ tự chỉnh sửa chỉ gồm tên và số liên hệ; không nhận email, mật khẩu hay quyền hạn.
 * Bỏ qua/null giữ giá trị cũ, riêng số điện thoại rỗng xóa số đã lưu.
 */
public record UpdateProfileRequest(
        @JsonProperty("full_name")
        @Size(min = 1, max = 150, message = "Họ tên phải có từ 1 đến 150 ký tự.")
        @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Họ tên không được chứa ký tự điều khiển.")
        String fullName,
        @Pattern(
                regexp = "(?:\\+?[0-9]{8,15})?",
                message = "Số điện thoại phải có 8 đến 15 chữ số, có thể bắt đầu bằng +.")
        String phone,
        @jakarta.validation.Valid @JsonProperty("default_address") DefaultAddressRequest defaultAddress,
        @JsonProperty("clear_default_address") Boolean clearDefaultAddress) {

    public UpdateProfileRequest(String fullName, String phone) { this(fullName, phone, null, false); }

    /** Chuẩn hóa như checkout; không tự đổi mã quốc gia hoặc bắt buộc khách lưu số điện thoại. */
    public UpdateProfileRequest {
        fullName = fullName == null ? null : fullName.strip();
        phone = phone == null ? null : phone.strip().replaceAll("[\\s()-]", "");
    }
}
