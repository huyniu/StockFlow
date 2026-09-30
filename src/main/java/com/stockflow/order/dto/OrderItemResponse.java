package com.stockflow.order.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.OrderItem;
import java.math.BigDecimal;
/** Snapshot mặt hàng, giá và thành tiền trả về cho khách hàng. */
public record OrderItemResponse(Long id, @JsonProperty("product_id") Long productId, int quantity,
 @JsonProperty("unit_price") BigDecimal unitPrice, @JsonProperty("line_total") BigDecimal lineTotal) {
 /** Chuyển dòng đơn sang DTO. */
 public static OrderItemResponse from(OrderItem i) {
  return new OrderItemResponse(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
 }
}
