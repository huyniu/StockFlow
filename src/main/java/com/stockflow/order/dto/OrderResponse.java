package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.domain.Shipment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Phản hồi đơn chứa vòng đời, bản chụp mặt hàng/người nhận và vận đơn; quyền được kiểm tra trước khi ánh xạ. */
public record OrderResponse(
        Long id,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("customer_id") Long customerId,
        @JsonProperty("warehouse_id") Long warehouseId,
        OrderStatus status,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("reservation_expires_at") Instant reservationExpiresAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<OrderItemResponse> items,
        ShipmentResponse shipment,
        DeliveryDetailsResponse delivery) {

    /** Chuyển đơn mới chưa có vận đơn sang DTO, giữ cách gọi cũ cho nghiệp vụ tạo đơn. */
    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    /** Chuyển đơn, mặt hàng và vận đơn sang DTO trong transaction đang mở. */
    public static OrderResponse from(Order order, Shipment shipment) {
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getCustomerId(),
                order.getWarehouseId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getReservationExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                ShipmentResponse.from(shipment),
                DeliveryDetailsResponse.from(order.getDelivery()));
    }
}
