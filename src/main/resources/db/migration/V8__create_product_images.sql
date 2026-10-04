-- Lưu ảnh bổ sung theo thứ tự; ảnh bìa products.image_url tiếp tục dùng cho kệ hàng.
-- Không sao chép ảnh minh họa hoặc thay đổi sản phẩm, tồn kho và sổ cái đã có.
CREATE TABLE product_images (
    product_id BIGINT NOT NULL REFERENCES products(id),
    position INTEGER NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    CONSTRAINT pk_product_images PRIMARY KEY (product_id, position),
    CONSTRAINT ck_product_images_position CHECK (position >= 0 AND position < 8),
    CONSTRAINT ck_product_images_url CHECK (CHAR_LENGTH(TRIM(image_url)) > 0)
);
