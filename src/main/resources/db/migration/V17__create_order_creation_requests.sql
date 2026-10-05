-- Khóa đặt hàng theo khách và lần gửi; đơn, reserve và bản ghi này cùng commit hoặc cùng rollback.
CREATE TABLE order_creation_requests (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id BIGINT UNIQUE REFERENCES orders(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_creation_customer_key UNIQUE (customer_id, request_key),
    CONSTRAINT ck_order_creation_key CHECK (LENGTH(TRIM(request_key)) BETWEEN 1 AND 128),
    CONSTRAINT ck_order_creation_hash CHECK (LENGTH(request_hash) = 64)
);

-- Không lưu bản sao địa chỉ trong khóa; request_hash chỉ dùng để phát hiện tái dùng khóa sai nội dung.
COMMENT ON TABLE order_creation_requests IS
    'Chống tạo đơn trùng khi khách gửi lại yêu cầu; chỉ lưu khóa, dấu vân tay và liên kết đơn.';
