-- Thông số nhập tay và nhóm màu; không đổi SKU, tồn kho, ledger hoặc đơn hàng đã có.
CREATE TABLE product_specifications (
    product_id BIGINT NOT NULL REFERENCES products(id),
    position INTEGER NOT NULL,
    specification_name VARCHAR(100) NOT NULL,
    specification_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (product_id, position),
    CONSTRAINT ck_product_specification_position CHECK (position >= 0 AND position < 60),
    CONSTRAINT ck_product_specification_name CHECK (LENGTH(TRIM(specification_name)) > 0),
    CONSTRAINT ck_product_specification_value CHECK (LENGTH(TRIM(specification_value)) > 0)
);

-- SKU gốc cũng được khai báo màu tại đây, tránh hai màu cùng tên trong một nhóm.
CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    sku_product_id BIGINT NOT NULL REFERENCES products(id),
    color_name VARCHAR(80) NOT NULL,
    color_key VARCHAR(80) NOT NULL,
    color_hex VARCHAR(7),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_product_variant_sku UNIQUE (sku_product_id),
    CONSTRAINT uq_product_variant_color UNIQUE (product_id, color_key),
    CONSTRAINT ck_product_variant_color CHECK (LENGTH(TRIM(color_name)) > 0),
    CONSTRAINT ck_product_variant_key CHECK (LENGTH(TRIM(color_key)) > 0)
);

CREATE INDEX idx_product_variants_product
    ON product_variants (product_id, id);
