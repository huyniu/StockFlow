package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dòng tóm tắt trong danh sách đơn vận hành, không tải toàn bộ mặt hàng khi phân trang.
 * Người vận hành lấy chi tiết qua API riêng, với quyền trên từng đơn vẫn được kiểm tra.
 */
public record OrderListResponse(
        Long id,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("customer_id") Long customerId,
        @JsonProperty("warehouse_id") Long warehouseId,
        @JsonProperty("warehouse_name") String warehouseName,
        OrderStatus status,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("reservation_expires_at") Instant reservationExpiresAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {
}
