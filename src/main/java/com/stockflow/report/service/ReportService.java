package com.stockflow.report.service;

import com.stockflow.common.exception.BadRequestException;
import com.stockflow.report.dto.*;
import com.stockflow.report.repository.ReportRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chỉ MANAGER và ADMIN được đọc báo cáo kinh doanh; mọi truy vấn chạy trong transaction chỉ đọc. */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ReportService {

    private final ReportRepository reports;

    /** Nhận repository tổng hợp số liệu tại database. */
    public ReportService(ReportRepository reports) {
        this.reports = reports;
    }

    /** Kiểm tra khoảng ngày và trả doanh thu theo đơn vị nhóm đã chọn. */
    public Page<RevenueReportResponse> revenue(
            LocalDate fromDate, LocalDate toDate, Long warehouseId, ReportPeriod period, Pageable pageable) {
        validateDates(fromDate, toDate);
        validateWarehouse(warehouseId);
        return reports.revenue(fromDate, toDate, warehouseId, period, reportPage(pageable, null));
    }

    /** limit là cách viết ngắn của size; vẫn dùng page để xem các trang tiếp theo. */
    public Page<TopProductReportResponse> topProducts(
            LocalDate fromDate, LocalDate toDate, Integer limit, Pageable pageable) {
        validateDates(fromDate, toDate);
        return reports.topProducts(fromDate, toDate, reportPage(pageable, limit));
    }

    /** Ngưỡng cảnh báo không âm, mặc định 10 được controller cung cấp. */
    public Page<LowStockReportResponse> lowStock(int threshold, Long warehouseId, Pageable pageable) {
        if (threshold < 0) {
            throw new BadRequestException("Ngưỡng cảnh báo không được âm.");
        }
        validateWarehouse(warehouseId);
        return reports.lowStock(threshold, warehouseId, reportPage(pageable, null));
    }

    /** Trả tổng quan toàn hệ thống theo tất cả trạng thái đang có dữ liệu. */
    public List<OrderSummaryResponse> orderSummary() {
        return reports.orderSummary();
    }

    /** Chặn khoảng ngày đảo ngược hoặc toDate không thể chuyển sang ngày kế tiếp. */
    private void validateDates(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("fromDate không được sau toDate.");
        }
        if (LocalDate.MAX.equals(toDate)) {
            throw new BadRequestException("toDate vượt giới hạn ngày báo cáo.");
        }
    }

    /** Mã kho phải dương; kho không có dữ liệu trả trang rỗng. */
    private void validateWarehouse(Long warehouseId) {
        if (warehouseId != null && warehouseId <= 0) {
            throw new BadRequestException("warehouseId phải lớn hơn 0.");
        }
    }

    /** Giới hạn 100 dòng mỗi trang và giữ thứ tự báo cáo cố định tại repository. */
    private Pageable reportPage(Pageable pageable, Integer limit) {
        int size = limit != null ? limit : pageable.getPageSize();
        if (size < 1 || size > 100) {
            throw new BadRequestException("Kích thước trang hoặc limit phải từ 1 đến 100.");
        }
        return PageRequest.of(pageable.getPageNumber(), size);
    }
}
