-- H2 không hỗ trợ partial covering index như PostgreSQL.
-- Index thông thường giữ kiểm chứng Flyway và điều kiện lọc ngày/trạng thái trong test chức năng.
CREATE INDEX idx_orders_report_created
    ON orders (created_at, status);
