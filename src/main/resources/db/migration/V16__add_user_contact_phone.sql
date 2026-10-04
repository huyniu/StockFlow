-- Bổ sung số liên hệ tùy chọn; không suy đoán dữ liệu hoặc sửa bản chụp người nhận của đơn cũ.
ALTER TABLE users
    ADD COLUMN phone VARCHAR(30);

-- Chỉ lưu dạng chuẩn hóa: 8–15 chữ số và dấu + tùy chọn; null nghĩa là chưa bổ sung hoặc đã xóa.
ALTER TABLE users
    ADD CONSTRAINT ck_users_phone
    CHECK (
        phone IS NULL
        OR phone ~ '^[+]?[0-9]{8,15}$'
    );
