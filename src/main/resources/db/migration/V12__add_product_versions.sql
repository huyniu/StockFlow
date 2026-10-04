-- Một trang model chứa nhiều phiên bản; mỗi phiên bản có các SKU màu và tồn kho riêng.
CREATE TABLE product_versions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    name VARCHAR(160) NOT NULL,
    name_key VARCHAR(160) NOT NULL,
    CONSTRAINT uq_product_version_name UNIQUE (product_id, name_key),
    CONSTRAINT uq_product_version_parent UNIQUE (product_id, id),
    CONSTRAINT ck_product_version_name CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_product_version_key CHECK (LENGTH(TRIM(name_key)) > 0)
);

-- Thông số riêng ghi đè nhãn tương ứng trong bảng thông số chung; không suy đoán từ tên sản phẩm.
CREATE TABLE product_version_specifications (
    version_id BIGINT NOT NULL REFERENCES product_versions(id),
    position INTEGER NOT NULL,
    specification_name VARCHAR(100) NOT NULL,
    specification_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (version_id, position),
    CONSTRAINT ck_version_specification_position CHECK (position >= 0 AND position < 60),
    CONSTRAINT ck_version_specification_name CHECK (LENGTH(TRIM(specification_name)) > 0),
    CONSTRAINT ck_version_specification_value CHECK (LENGTH(TRIM(specification_value)) > 0)
);

ALTER TABLE product_variants
    ADD COLUMN version_id BIGINT;

-- Chuyển nguyên nhóm màu V11 vào một phiên bản trung tính; giữ ID, giá, ảnh, tồn và lịch sử.
INSERT INTO product_versions (product_id, name, name_key)
SELECT DISTINCT product_id, 'Phiên bản hiện tại', 'phiên bản hiện tại'
FROM product_variants;

UPDATE product_variants AS variant
SET version_id = (
    SELECT version.id
    FROM product_versions AS version
    WHERE version.product_id = variant.product_id
);

ALTER TABLE product_variants
    ALTER COLUMN version_id SET NOT NULL;

ALTER TABLE product_variants
    DROP CONSTRAINT uq_product_variant_color;

-- Cùng màu được phép ở các phiên bản khác nhau; không được lặp trong cùng phiên bản.
ALTER TABLE product_variants
    ADD CONSTRAINT uq_product_version_color UNIQUE (version_id, color_key);

-- Khóa ngoại ghép chặn SKU trỏ sang phiên bản của một model khác, kể cả khi ghi SQL trực tiếp.
ALTER TABLE product_variants
    ADD CONSTRAINT fk_product_variant_version
    FOREIGN KEY (product_id, version_id) REFERENCES product_versions(product_id, id);

CREATE INDEX idx_product_versions_product
    ON product_versions (product_id, id);

CREATE INDEX idx_product_variants_version
    ON product_variants (version_id, id);
