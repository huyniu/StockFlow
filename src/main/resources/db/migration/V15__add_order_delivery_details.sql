-- Lưu bản chụp người nhận trên đơn; giữ null cho đơn lịch sử và không sửa migration đã áp dụng.
ALTER TABLE orders
    ADD COLUMN recipient_name VARCHAR(150),
    ADD COLUMN recipient_phone VARCHAR(16),
    ADD COLUMN delivery_address VARCHAR(500),
    ADD COLUMN delivery_note VARCHAR(1000);

-- Đơn cũ được phép chưa có bản chụp; bản chụp mới phải đầy đủ, không nhận dữ liệu nửa chừng.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_delivery_details
    CHECK (
        (
            recipient_name IS NULL
            AND recipient_phone IS NULL
            AND delivery_address IS NULL
            AND delivery_note IS NULL
        )
        OR (
            recipient_name IS NOT NULL
            AND CHAR_LENGTH(TRIM(recipient_name)) > 0
            AND recipient_phone IS NOT NULL
            AND recipient_phone ~ '^\+?[0-9]{8,15}$'
            AND delivery_address IS NOT NULL
            AND CHAR_LENGTH(TRIM(delivery_address)) > 0
        )
    );
