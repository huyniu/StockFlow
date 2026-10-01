package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.Shipment;
import com.stockflow.order.domain.ShipmentStatus;
import java.time.Instant;

/** Thông tin theo dõi vận đơn trong response đơn hàng, không trả entity JPA ra API. */
public record ShipmentResponse(
        @JsonProperty("tracking_code") String trackingCode,
        ShipmentStatus status,
        @JsonProperty("shipped_at") Instant shippedAt,
        @JsonProperty("delivered_at") Instant deliveredAt) {

    /** Chụp trạng thái vận đơn; đơn chưa đóng gói trả shipment null. */
    public static ShipmentResponse from(Shipment shipment) {
        if (shipment == null) {
            return null;
        }
        return new ShipmentResponse(
                shipment.getTrackingCode(), shipment.getStatus(),
                shipment.getShippedAt(), shipment.getDeliveredAt());
    }
}
