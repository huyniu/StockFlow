package com.stockflow.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Doanh thu theo ngày hoặc tháng UTC và kho; chỉ tính các đơn đã xác nhận còn hiệu lực. */
public record RevenueReportResponse(
        LocalDate period,
        @JsonProperty("warehouse_id") Long warehouseId,
        @JsonProperty("warehouse_name") String warehouseName,
        @JsonProperty("total_orders") long totalOrders,
        @JsonProperty("total_revenue") BigDecimal totalRevenue) {
}
