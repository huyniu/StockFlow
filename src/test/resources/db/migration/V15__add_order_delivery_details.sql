-- Migration H2 tương ứng V15: giữ đơn lịch sử và kiểm tra bản chụp người nhận đầy đủ.
ALTER TABLE orders ADD COLUMN recipient_name VARCHAR(150);
ALTER TABLE orders ADD COLUMN recipient_phone VARCHAR(16);
ALTER TABLE orders ADD COLUMN delivery_address VARCHAR(500);
ALTER TABLE orders ADD COLUMN delivery_note VARCHAR(1000);

-- H2 dùng REGEXP_LIKE thay cho toán tử ~ của PostgreSQL; cùng giới hạn số điện thoại.
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
            AND REGEXP_LIKE(recipient_phone, '^\+?[0-9]{8,15}$')
            AND delivery_address IS NOT NULL
            AND CHAR_LENGTH(TRIM(delivery_address)) > 0
        )
    );
