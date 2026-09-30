package com.stockflow.report.api;

import com.stockflow.common.dto.PageResponse;
import com.stockflow.report.dto.*;
import com.stockflow.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** API báo cáo nội bộ; CUSTOMER và WAREHOUSE_STAFF bị chặn ở cả controller và service. */
@RestController
@Tag(name = "Reports", description = "Business reports for MANAGER and ADMIN")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ReportController {

    private final ReportService reports;

    /** Nhận dịch vụ báo cáo để controller chỉ xử lý contract HTTP. */
    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** Nhóm doanh thu theo DAY hoặc MONTH; khoảng ngày bao gồm cả fromDate và toDate theo UTC. */
    @GetMapping("/revenue")
    @Operation(summary = "Aggregate revenue by UTC day or month and warehouse")
    public PageResponse<RevenueReportResponse> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "DAY") ReportPeriod groupBy,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(reports.revenue(fromDate, toDate, warehouseId, groupBy, pageable));
    }

    /** Trả sản phẩm bán chạy theo doanh thu; limit tùy chọn ghi đè size, page vẫn được áp dụng. */
    @GetMapping("/top-products")
    @Operation(summary = "Rank products by snapshot sales revenue")
    public PageResponse<TopProductReportResponse> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer limit,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(reports.topProducts(fromDate, toDate, limit, pageable));
    }

    /** Cảnh báo theo availableQuantity tăng dần; threshold mặc định 10 và có thể lọc theo kho. */
    @GetMapping("/low-stock")
    @Operation(summary = "Find inventory at or below the warning threshold")
    public PageResponse<LowStockReportResponse> lowStock(
            @RequestParam(defaultValue = "10") int threshold,
            @RequestParam(required = false) Long warehouseId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(reports.lowStock(threshold, warehouseId, pageable));
    }

    /** Tổng hợp số lượng và tổng tiền theo trạng thái cho MANAGER/ADMIN. */
    @GetMapping("/order-summary")
    @Operation(summary = "Summarize order counts and amounts by status")
    public List<OrderSummaryResponse> orderSummary() {
        return reports.orderSummary();
    }
}
