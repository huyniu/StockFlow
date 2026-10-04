package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.DeliveryDetails;

/** Thông tin nhận hàng chỉ trả cùng đơn sau khi đã kiểm tra chủ sở hữu hoặc phạm vi kho. */
public record DeliveryDetailsResponse(
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("recipient_phone") String recipientPhone,
        String address,
        String note) {

    /** Đơn trước V15 trả delivery=null; không suy đoán người nhận hoặc địa chỉ lịch sử. */
    public static DeliveryDetailsResponse from(DeliveryDetails delivery) {
        if (delivery == null) {
            return null;
        }
        return new DeliveryDetailsResponse(
                delivery.getRecipientName(), delivery.getRecipientPhone(), delivery.getAddress(), delivery.getNote());
    }
}
