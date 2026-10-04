-- Đồng bộ cột ảnh sản phẩm với PostgreSQL bằng cú pháp ALTER TABLE tương thích H2.
ALTER TABLE products
    ADD COLUMN image_url VARCHAR(2048);
