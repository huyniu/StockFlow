-- Tách danh mục Điện thoại và thương hiệu bằng khóa ngoại, giữ nguyên ID sản phẩm/tồn kho/lịch sử.
-- Chỉ gán Apple cho sản phẩm đã thuộc danh mục iphone; không đoán hãng của những sản phẩm khác.
CREATE TABLE brands (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    slug VARCHAR(180) NOT NULL UNIQUE
);

CREATE TABLE brand_categories (
    brand_id BIGINT NOT NULL REFERENCES brands(id),
    category_id BIGINT NOT NULL REFERENCES categories(id),
    CONSTRAINT pk_brand_categories PRIMARY KEY (brand_id, category_id)
);

ALTER TABLE products
    ADD COLUMN brand_id BIGINT REFERENCES brands(id);

CREATE INDEX idx_products_category_brand_status
    ON products(category_id, brand_id, status);

CREATE INDEX idx_brand_categories_category
    ON brand_categories(category_id, brand_id);

-- Chuẩn bị các hãng điện thoại trong ảnh để ADMIN chọn khi thêm sản phẩm mới.
INSERT INTO brands (name, slug)
VALUES
    ('Apple', 'apple'),
    ('Samsung', 'samsung'),
    ('OPPO', 'oppo'),
    ('Xiaomi', 'xiaomi'),
    ('TECNO', 'tecno'),
    ('HONOR', 'honor'),
    ('nubia', 'nubia'),
    ('Sony', 'sony'),
    ('Nokia', 'nokia'),
    ('Nothing', 'nothing'),
    ('Masstel', 'masstel'),
    ('Huawei', 'huawei'),
    ('Meizu', 'meizu'),
    ('realme', 'realme'),
    ('itel', 'itel'),
    ('Infinix', 'infinix');

UPDATE products
SET brand_id = (
    SELECT id
    FROM brands
    WHERE slug = 'apple'
)
WHERE category_id IN (
    SELECT id
    FROM categories
    WHERE slug = 'iphone'
);

-- Đổi danh mục iphone tại chỗ nếu chưa có Điện thoại: mọi liên kết sản phẩm vẫn giữ ID cũ.
UPDATE categories
SET name = 'Điện thoại',
    slug = 'dien-thoai'
WHERE slug = 'iphone'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'dien-thoai'
  );

-- Database mới cũng có danh mục Điện thoại để menu hãng và form dùng chung dữ liệu tham chiếu.
INSERT INTO categories (name, slug)
SELECT 'Điện thoại', 'dien-thoai'
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE slug = 'dien-thoai'
);

-- Nếu Điện thoại đã tồn tại, gộp sản phẩm iphone vào mục đó trước khi bỏ mục iphone rỗng.
UPDATE products
SET category_id = (
    SELECT id
    FROM categories
    WHERE slug = 'dien-thoai'
)
WHERE category_id IN (
    SELECT id
    FROM categories
    WHERE slug = 'iphone'
);

DELETE FROM categories
WHERE slug = 'iphone'
  AND NOT EXISTS (
      SELECT 1
      FROM products
      WHERE products.category_id = categories.id
  );

-- Nhóm Điện thoại, Tablet vừa tạo còn rỗng được tách thành Máy tính bảng.
-- Nếu nhóm này đã có hàng thì giữ nguyên để không tự suy đoán hàng đó là điện thoại hay tablet.
UPDATE categories
SET name = 'Máy tính bảng',
    slug = 'may-tinh-bang'
WHERE slug = 'dien-thoai-tablet'
  AND NOT EXISTS (
      SELECT 1
      FROM products
      WHERE products.category_id = categories.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'may-tinh-bang'
         OR name = 'Máy tính bảng'
  );

-- Hãng có thể được gợi ý ở nhiều danh mục; đây không phải cây danh mục con.
INSERT INTO brand_categories (brand_id, category_id)
SELECT brands.id, categories.id
FROM brands
CROSS JOIN categories
WHERE categories.slug = 'dien-thoai';
