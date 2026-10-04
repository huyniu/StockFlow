-- Lưu mô tả dưới dạng văn bản; sản phẩm cũ có thể chưa có nội dung chi tiết.
-- Giới hạn cùng API để dữ liệu nhập trực tiếp vào PostgreSQL cũng được kiểm soát.
ALTER TABLE products
    ADD COLUMN description TEXT;

ALTER TABLE products
    ADD CONSTRAINT ck_products_description_length
    CHECK (description IS NULL OR CHAR_LENGTH(description) <= 5000);
