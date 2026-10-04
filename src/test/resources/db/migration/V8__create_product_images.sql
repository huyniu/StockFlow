-- H2 dùng cùng cấu trúc ảnh bổ sung và giới hạn vị trí như PostgreSQL để kiểm thử lưu/thay thứ tự.
-- Ảnh bìa và các migration đã áp dụng không bị viết lại.
CREATE TABLE product_images (
    product_id BIGINT NOT NULL REFERENCES products(id),
    position INTEGER NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    CONSTRAINT pk_product_images PRIMARY KEY (product_id, position),
    CONSTRAINT ck_product_images_position CHECK (position >= 0 AND position < 8),
    CONSTRAINT ck_product_images_url CHECK (CHAR_LENGTH(TRIM(image_url)) > 0)
);
