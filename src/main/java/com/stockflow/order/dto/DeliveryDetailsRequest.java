package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Thông tin nhận hàng cho đơn mới; không lấy lại từ hồ sơ khi xử lý hoặc giao đơn. */
public record DeliveryDetailsRequest(
        @JsonProperty("recipient_name")
        @NotBlank(message = "Tên người nhận không được để trống.")
        @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự.") String recipientName,
        @JsonProperty("recipient_phone")
        @NotBlank(message = "Số điện thoại người nhận không được để trống.")
        @Pattern(regexp = "\\+?[0-9]{8,15}", message = "Số điện thoại gồm 8–15 chữ số, có thể bắt đầu bằng +.")
        String recipientPhone,
        @NotBlank(message = "Địa chỉ giao hàng không được để trống.")
        @Size(max = 500, message = "Địa chỉ giao hàng tối đa 500 ký tự.") String address,
        @Size(max = 1000, message = "Ghi chú giao hàng tối đa 1.000 ký tự.") String note) {

    /** Bỏ khoảng trắng đầu/cuối và dấu định dạng điện thoại; ghi chú trống được lưu là null. */
    public DeliveryDetailsRequest {
        recipientName = strip(recipientName);
        recipientPhone = recipientPhone == null ? null : recipientPhone.strip().replaceAll("[\\s()-]", "");
        address = strip(address);
        note = strip(note);
        if (note != null && note.isEmpty()) {
            note = null;
        }
    }

    /** Giữ null để Bean Validation báo thiếu trường, không tự điền dữ liệu người nhận. */
    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
