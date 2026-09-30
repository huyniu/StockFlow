package com.stockflow.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.OrderStatus;
import java.math.BigDecimal;

/** Tổng hợp tất cả đơn theo trạng thái; tổng tiền của nhóm không đồng nghĩa với doanh thu đã thu. */
public record OrderSummaryResponse(
        @JsonProperty("order_status") OrderStatus orderStatus,
        @JsonProperty("total_count") long totalCount,
        @JsonProperty("total_amount") BigDecimal totalAmount) {
}
