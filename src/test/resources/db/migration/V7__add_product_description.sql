-- Bản H2 dùng cùng cột mô tả và giới hạn độ dài như PostgreSQL thật.
-- Cột tùy chọn giúp giữ nguyên sản phẩm, đơn hàng và tồn kho hiện có.
ALTER TABLE products
    ADD COLUMN description TEXT;

ALTER TABLE products
    ADD CONSTRAINT ck_products_description_length
    CHECK (description IS NULL OR CHAR_LENGTH(description) <= 5000);
