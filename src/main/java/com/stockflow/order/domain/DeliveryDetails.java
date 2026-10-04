package com.stockflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** Bản chụp thông tin nhận hàng của một đơn; không có phương thức sửa và không phụ thuộc hồ sơ khách. */
@Embeddable
public class DeliveryDetails {

    @Column(name = "recipient_name", length = 150, updatable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 16, updatable = false)
    private String recipientPhone;

    @Column(name = "delivery_address", length = 500, updatable = false)
    private String address;

    @Column(name = "delivery_note", length = 1000, updatable = false)
    private String note;

    /** JPA phục hồi bản chụp; các cột null của đơn trước V15 vẫn được hỗ trợ. */
    protected DeliveryDetails() {}

    /** Dịch vụ chỉ tạo bản chụp sau khi đã kiểm tra DTO, không tự lấy địa chỉ từ hồ sơ. */
    public DeliveryDetails(String recipientName, String recipientPhone, String address, String note) {
        this.recipientName = Objects.requireNonNull(recipientName);
        this.recipientPhone = Objects.requireNonNull(recipientPhone);
        this.address = Objects.requireNonNull(address);
        this.note = note;
    }

    /** Lấy tên người nhận đã chụp lúc đặt hàng. */
    public String getRecipientName() {
        return recipientName;
    }

    /** Lấy số điện thoại đã chuẩn hóa lúc đặt hàng. */
    public String getRecipientPhone() {
        return recipientPhone;
    }

    /** Lấy địa chỉ giao hàng cố định của đơn. */
    public String getAddress() {
        return address;
    }

    /** Lấy ghi chú tùy chọn; null nghĩa là khách không nhập ghi chú. */
    public String getNote() {
        return note;
    }
}
