package com.stockflow.order.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
/** Phản hồi đơn hàng chứa vòng đời, thời hạn giữ chỗ và snapshot mặt hàng. */
public record OrderResponse(Long id, @JsonProperty("order_code") String orderCode,
 @JsonProperty("customer_id") Long customerId, @JsonProperty("warehouse_id") Long warehouseId,
 OrderStatus status, @JsonProperty("total_amount") BigDecimal totalAmount,
 @JsonProperty("reservation_expires_at") Instant reservationExpiresAt,
 @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt,
 List<OrderItemResponse> items) {
 /** Chuyển đơn và các mặt hàng sang DTO trong transaction đang mở. */
 public static OrderResponse from(Order o) {
  return new OrderResponse(o.getId(), o.getOrderCode(), o.getCustomerId(), o.getWarehouseId(), o.getStatus(),
   o.getTotalAmount(), o.getReservationExpiresAt(), o.getCreatedAt(), o.getUpdatedAt(),
   o.getItems().stream().map(OrderItemResponse::from).toList());
 }
}
