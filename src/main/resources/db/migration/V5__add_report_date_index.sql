-- Đo trước/sau cho thấy Top Products lọc ngày phải quét orders khi không có warehouseId.
-- Index một phần chỉ lưu đơn đóng góp doanh thu, kèm cột cần đọc để có thể dùng Index Only Scan.
CREATE INDEX idx_orders_report_created
    ON orders (created_at)
    INCLUDE (id, warehouse_id, total_amount)
    WHERE status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED');
