-- Đơn hàng giữ chỗ, thanh toán mô phỏng và vận chuyển theo vòng đời đã chốt.
CREATE TABLE orders (
 id BIGSERIAL PRIMARY KEY,
 order_code VARCHAR(50) NOT NULL UNIQUE,
 customer_id BIGINT NOT NULL REFERENCES users(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','PACKED','SHIPPED','DELIVERED','CANCELLED','EXPIRED','RETURNED')),
 total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
 reservation_expires_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Giá và thành tiền được chụp lúc đặt hàng, không phụ thuộc giá catalog về sau.
CREATE TABLE order_items (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id),
 product_id BIGINT NOT NULL REFERENCES products(id),
 quantity INTEGER NOT NULL CHECK (quantity > 0),
 unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
 line_total NUMERIC(12,2) NOT NULL CHECK (line_total >= 0),
 CONSTRAINT uq_order_product UNIQUE(order_id, product_id)
);

-- Một đơn chỉ có một bản ghi thanh toán để bảo đảm gọi lặp không thu tiền nhiều lần.
CREATE TABLE payments (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
 status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING','PAID','FAILED','REFUNDED')),
 amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
 method VARCHAR(50) NOT NULL,
 paid_at TIMESTAMPTZ
);

-- Mỗi đơn có tối đa một vận đơn trong phạm vi MVP.
CREATE TABLE shipments (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
 tracking_code VARCHAR(100) NOT NULL UNIQUE,
 status VARCHAR(50) NOT NULL CHECK (status IN ('PREPARING','SHIPPED','DELIVERED','RETURNED')),
 shipped_at TIMESTAMPTZ,
 delivered_at TIMESTAMPTZ
);

-- Các chỉ mục phục vụ lịch sử khách hàng, công việc tại kho và quét đơn hết hạn.
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);
CREATE INDEX idx_orders_warehouse_status_created ON orders(warehouse_id, status, created_at DESC);
CREATE INDEX idx_orders_status_expiry ON orders(status, reservation_expires_at);
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- Tác vụ hết hạn dùng actor hệ thống riêng, không gán nhầm hành động tự động cho khách hàng.
-- Chuỗi password_hash không phải BCrypt hợp lệ nên tài khoản này không thể đăng nhập bằng mật khẩu.
INSERT INTO users(email, password_hash, full_name, role_id, status)
SELECT 'inventory-expiry@stockflow.invalid', 'SYSTEM_ACCOUNT_NO_LOGIN', 'Hệ thống hết hạn đơn hàng', id, 'INACTIVE'
FROM roles WHERE name = 'CUSTOMER';
