-- Bổ sung cây danh mục và hãng theo bốn ảnh tham khảo CellphoneS; không tạo sản phẩm hoặc tồn mẫu.
-- Bảng tham chiếu tạm phục vụ riêng migration, được xóa sau khi hoàn tất; dữ liệu cũ giữ nguyên ID.
ALTER TABLE categories
    ADD COLUMN parent_id BIGINT REFERENCES categories(id);

ALTER TABLE categories
    ADD CONSTRAINT ck_category_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id);

CREATE INDEX idx_categories_parent
    ON categories(parent_id);

CREATE TABLE stockflow_catalog_category_seed (
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL PRIMARY KEY,
    parent_slug VARCHAR(180)
);

INSERT INTO stockflow_catalog_category_seed (name, slug, parent_slug)
VALUES
    ('Laptop', 'laptop', NULL),
    ('Âm thanh, Mic thu âm', 'am-thanh-mic-thu-am', NULL),
    ('Đồng hồ, Camera', 'dong-ho-camera', NULL),
    ('Đồ gia dụng, Làm đẹp', 'do-gia-dung-lam-dep', NULL),
    ('Tai nghe', 'tai-nghe', 'am-thanh-mic-thu-am'),
    ('Tai nghe Bluetooth', 'tai-nghe-bluetooth', 'tai-nghe'),
    ('Tai nghe chụp tai', 'tai-nghe-chup-tai', 'tai-nghe'),
    ('Tai nghe nhét tai', 'tai-nghe-nhet-tai', 'tai-nghe'),
    ('Tai nghe có dây', 'tai-nghe-co-day', 'tai-nghe'),
    ('Tai nghe thể thao', 'tai-nghe-the-thao', 'tai-nghe'),
    ('Tai nghe gaming', 'tai-nghe-gaming', 'tai-nghe'),
    ('Mic thu âm', 'mic-thu-am', 'am-thanh-mic-thu-am'),
    ('Mic cài áo', 'mic-cai-ao', 'mic-thu-am'),
    ('Mic phòng thu, podcast', 'mic-phong-thu-podcast', 'mic-thu-am'),
    ('Mic livestream', 'mic-livestream', 'mic-thu-am'),
    ('Micro không dây', 'micro-khong-day', 'mic-thu-am'),
    ('Loa', 'loa', 'am-thanh-mic-thu-am'),
    ('Loa Bluetooth', 'loa-bluetooth', 'loa'),
    ('Loa karaoke', 'loa-karaoke', 'loa'),
    ('Loa kéo', 'loa-keo', 'loa'),
    ('Loa soundbar', 'loa-soundbar', 'loa'),
    ('Loa vi tính', 'loa-vi-tinh', 'loa'),
    ('Đồng hồ', 'dong-ho', 'dong-ho-camera'),
    ('Đồng hồ thông minh', 'dong-ho-thong-minh', 'dong-ho'),
    ('Vòng đeo tay thông minh', 'vong-deo-tay-thong-minh', 'dong-ho'),
    ('Đồng hồ định vị trẻ em', 'dong-ho-dinh-vi-tre-em', 'dong-ho'),
    ('Dây đồng hồ thông minh', 'day-dong-ho-thong-minh', 'dong-ho'),
    ('Camera', 'camera', 'dong-ho-camera'),
    ('Camera an ninh', 'camera-an-ninh', 'camera'),
    ('Camera hành trình', 'camera-hanh-trinh', 'camera'),
    ('Action camera', 'action-camera', 'camera'),
    ('Camera AI', 'camera-ai', 'camera'),
    ('Gimbal', 'gimbal', 'camera'),
    ('Tripod', 'tripod', 'camera'),
    ('Máy ảnh', 'may-anh', 'camera'),
    ('Flycam', 'flycam', 'camera'),
    ('Thiết bị gia đình', 'thiet-bi-gia-dinh', 'do-gia-dung-lam-dep'),
    ('Quạt', 'quat', 'thiet-bi-gia-dinh'),
    ('Robot hút bụi', 'robot-hut-bui', 'thiet-bi-gia-dinh'),
    ('Máy chiếu', 'may-chieu', 'thiet-bi-gia-dinh'),
    ('Máy lọc không khí', 'may-loc-khong-khi', 'thiet-bi-gia-dinh'),
    ('Máy hút ẩm', 'may-hut-am', 'thiet-bi-gia-dinh'),
    ('Máy hút bụi cầm tay', 'may-hut-bui-cam-tay', 'thiet-bi-gia-dinh'),
    ('TV Box', 'tv-box', 'thiet-bi-gia-dinh'),
    ('Máy sưởi, quạt sưởi', 'may-suoi-quat-suoi', 'thiet-bi-gia-dinh'),
    ('Bàn ủi', 'ban-ui', 'thiet-bi-gia-dinh'),
    ('Gia dụng nhà bếp', 'gia-dung-nha-bep', 'do-gia-dung-lam-dep'),
    ('Nồi chiên không dầu', 'noi-chien-khong-dau', 'gia-dung-nha-bep'),
    ('Nồi cơm điện', 'noi-com-dien', 'gia-dung-nha-bep'),
    ('Máy xay sinh tố', 'may-xay-sinh-to', 'gia-dung-nha-bep'),
    ('Máy ép trái cây', 'may-ep-trai-cay', 'gia-dung-nha-bep'),
    ('Máy làm sữa hạt', 'may-lam-sua-hat', 'gia-dung-nha-bep'),
    ('Bếp điện', 'bep-dien', 'gia-dung-nha-bep'),
    ('Ấm siêu tốc', 'am-sieu-toc', 'gia-dung-nha-bep'),
    ('Nồi áp suất', 'noi-ap-suat', 'gia-dung-nha-bep'),
    ('Nồi nấu chậm', 'noi-nau-cham', 'gia-dung-nha-bep'),
    ('Nồi lẩu điện', 'noi-lau-dien', 'gia-dung-nha-bep'),
    ('Sức khỏe & Làm đẹp', 'suc-khoe-lam-dep', 'do-gia-dung-lam-dep'),
    ('Máy sấy tóc', 'may-say-toc', 'suc-khoe-lam-dep'),
    ('Máy massage', 'may-massage', 'suc-khoe-lam-dep'),
    ('Máy cạo râu', 'may-cao-rau', 'suc-khoe-lam-dep'),
    ('Cân sức khỏe', 'can-suc-khoe', 'suc-khoe-lam-dep'),
    ('Bàn chải điện', 'ban-chai-dien', 'suc-khoe-lam-dep'),
    ('Máy tăm nước', 'may-tam-nuoc', 'suc-khoe-lam-dep'),
    ('Tông đơ cắt tóc', 'tong-do-cat-toc', 'suc-khoe-lam-dep'),
    ('Máy tỉa lông mũi', 'may-tia-long-mui', 'suc-khoe-lam-dep'),
    ('Máy rửa mặt', 'may-rua-mat', 'suc-khoe-lam-dep'),
    ('Máy tạo kiểu tóc', 'may-tao-kieu-toc', 'suc-khoe-lam-dep'),
    ('Máy triệt lông', 'may-triet-long', 'suc-khoe-lam-dep'),
    ('Máy đo huyết áp', 'may-do-huyet-ap', 'suc-khoe-lam-dep');

-- Giữ danh mục người dùng đã tạo khi tên hoặc slug đã tồn tại; không nhân bản nhóm tham chiếu.
INSERT INTO categories (name, slug)
SELECT seed.name, seed.slug
FROM stockflow_catalog_category_seed seed
WHERE NOT EXISTS (
    SELECT 1
    FROM categories existing
    WHERE existing.slug = seed.slug
       OR existing.name = seed.name
);

-- Chỉ nối nhóm còn chưa có cha; không viết đè cây danh mục do người dùng đã tổ chức.
UPDATE categories
SET parent_id = (
    SELECT parent.id
    FROM stockflow_catalog_category_seed child_seed
    JOIN stockflow_catalog_category_seed parent_seed
        ON parent_seed.slug = child_seed.parent_slug
    JOIN categories parent
        ON parent.slug = parent_seed.slug
        OR parent.name = parent_seed.name
    WHERE child_seed.slug = categories.slug
       OR child_seed.name = categories.name
)
WHERE parent_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM stockflow_catalog_category_seed seed
      WHERE (seed.slug = categories.slug OR seed.name = categories.name)
        AND seed.parent_slug IS NOT NULL
  );

-- Nhóm Tai nghe & Loa cũ trở thành một nhánh Âm thanh; sản phẩm liên quan vẫn giữ category_id cũ.
UPDATE categories
SET parent_id = (
    SELECT id
    FROM categories
    WHERE slug = 'am-thanh-mic-thu-am'
)
WHERE slug = 'tai-nghe-loa'
  AND parent_id IS NULL;

CREATE TABLE stockflow_catalog_brand_seed (
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL PRIMARY KEY
);

INSERT INTO stockflow_catalog_brand_seed (name, slug)
VALUES
    ('Apple', 'apple'),
    ('ASUS', 'asus'),
    ('Lenovo', 'lenovo'),
    ('Dell', 'dell'),
    ('HP', 'hp'),
    ('Acer', 'acer'),
    ('LG', 'lg'),
    ('MSI', 'msi'),
    ('Gigabyte', 'gigabyte'),
    ('Microsoft Surface', 'microsoft-surface'),
    ('Masstel', 'masstel'),
    ('Samsung', 'samsung'),
    ('Colorful', 'colorful'),
    ('Sony', 'sony'),
    ('JBL', 'jbl'),
    ('Marshall', 'marshall'),
    ('Soundpeats', 'soundpeats'),
    ('Bose', 'bose'),
    ('Edifier', 'edifier'),
    ('Xiaomi', 'xiaomi'),
    ('Huawei', 'huawei'),
    ('Sennheiser', 'sennheiser'),
    ('Havit', 'havit'),
    ('Beats', 'beats'),
    ('Tronsmart', 'tronsmart'),
    ('Anker', 'anker'),
    ('Shokz', 'shokz'),
    ('Harman Kardon', 'harman-kardon'),
    ('Acnos', 'acnos'),
    ('Arirang', 'arirang'),
    ('Alpha Works', 'alpha-works'),
    ('Coros', 'coros'),
    ('Garmin', 'garmin'),
    ('Kieslect', 'kieslect'),
    ('Amazfit', 'amazfit'),
    ('Black Shark', 'black-shark'),
    ('Mibro', 'mibro'),
    ('imoo', 'imoo'),
    ('Kospet', 'kospet'),
    ('MyKID', 'mykid'),
    ('KAVVO', 'kavvo'),
    ('Imou', 'imou'),
    ('Ezviz', 'ezviz'),
    ('TP-Link', 'tp-link'),
    ('Tiandy', 'tiandy'),
    ('DJI', 'dji'),
    ('Insta360', 'insta360'),
    ('Fujifilm', 'fujifilm'),
    ('Canon', 'canon'),
    ('GoPro', 'gopro'),
    ('Philips', 'philips'),
    ('Panasonic', 'panasonic'),
    ('Sunhouse', 'sunhouse'),
    ('Sharp', 'sharp'),
    ('Gaabor', 'gaabor'),
    ('Bear', 'bear'),
    ('AQUA', 'aqua'),
    ('Toshiba', 'toshiba'),
    ('Midea', 'midea'),
    ('Dreame', 'dreame'),
    ('Cuckoo', 'cuckoo'),
    ('Ecovacs', 'ecovacs'),
    ('Tineco', 'tineco'),
    ('Dyson', 'dyson'),
    ('Roborock', 'roborock'),
    ('Wanbo', 'wanbo');

-- Apple là hãng chung, Mac/MacBook và Apple Watch là dòng sản phẩm, không tạo hãng trùng.
INSERT INTO brands (name, slug)
SELECT seed.name, seed.slug
FROM stockflow_catalog_brand_seed seed
WHERE NOT EXISTS (
    SELECT 1
    FROM brands existing
    WHERE existing.slug = seed.slug
       OR LOWER(existing.name) = LOWER(seed.name)
);

CREATE TABLE stockflow_catalog_brand_link_seed (
    brand_slug VARCHAR(180) NOT NULL,
    category_slug VARCHAR(180) NOT NULL,
    PRIMARY KEY (brand_slug, category_slug)
);

INSERT INTO stockflow_catalog_brand_link_seed (brand_slug, category_slug)
VALUES
    ('apple', 'laptop'),
    ('asus', 'laptop'),
    ('lenovo', 'laptop'),
    ('dell', 'laptop'),
    ('hp', 'laptop'),
    ('acer', 'laptop'),
    ('lg', 'laptop'),
    ('msi', 'laptop'),
    ('gigabyte', 'laptop'),
    ('microsoft-surface', 'laptop'),
    ('masstel', 'laptop'),
    ('samsung', 'laptop'),
    ('colorful', 'laptop'),
    ('apple', 'tai-nghe'),
    ('sony', 'tai-nghe'),
    ('jbl', 'tai-nghe'),
    ('samsung', 'tai-nghe'),
    ('marshall', 'tai-nghe'),
    ('soundpeats', 'tai-nghe'),
    ('bose', 'tai-nghe'),
    ('edifier', 'tai-nghe'),
    ('xiaomi', 'tai-nghe'),
    ('huawei', 'tai-nghe'),
    ('sennheiser', 'tai-nghe'),
    ('havit', 'tai-nghe'),
    ('beats', 'tai-nghe'),
    ('tronsmart', 'tai-nghe'),
    ('anker', 'tai-nghe'),
    ('shokz', 'tai-nghe'),
    ('jbl', 'loa'),
    ('marshall', 'loa'),
    ('harman-kardon', 'loa'),
    ('acnos', 'loa'),
    ('samsung', 'loa'),
    ('sony', 'loa'),
    ('arirang', 'loa'),
    ('lg', 'loa'),
    ('alpha-works', 'loa'),
    ('edifier', 'loa'),
    ('bose', 'loa'),
    ('tronsmart', 'loa'),
    ('apple', 'dong-ho'),
    ('samsung', 'dong-ho'),
    ('xiaomi', 'dong-ho'),
    ('huawei', 'dong-ho'),
    ('coros', 'dong-ho'),
    ('garmin', 'dong-ho'),
    ('kieslect', 'dong-ho'),
    ('amazfit', 'dong-ho'),
    ('black-shark', 'dong-ho'),
    ('mibro', 'dong-ho'),
    ('masstel', 'dong-ho'),
    ('imoo', 'dong-ho'),
    ('kospet', 'dong-ho'),
    ('mykid', 'dong-ho'),
    ('kavvo', 'dong-ho'),
    ('imou', 'camera'),
    ('ezviz', 'camera'),
    ('xiaomi', 'camera'),
    ('tp-link', 'camera'),
    ('tiandy', 'camera'),
    ('dji', 'camera'),
    ('insta360', 'camera'),
    ('fujifilm', 'camera'),
    ('canon', 'camera'),
    ('sony', 'camera'),
    ('gopro', 'camera'),
    ('philips', 'do-gia-dung-lam-dep'),
    ('panasonic', 'do-gia-dung-lam-dep'),
    ('sunhouse', 'do-gia-dung-lam-dep'),
    ('sharp', 'do-gia-dung-lam-dep'),
    ('gaabor', 'do-gia-dung-lam-dep'),
    ('bear', 'do-gia-dung-lam-dep'),
    ('aqua', 'do-gia-dung-lam-dep'),
    ('toshiba', 'do-gia-dung-lam-dep'),
    ('midea', 'do-gia-dung-lam-dep'),
    ('dreame', 'do-gia-dung-lam-dep'),
    ('xiaomi', 'do-gia-dung-lam-dep'),
    ('cuckoo', 'do-gia-dung-lam-dep'),
    ('ecovacs', 'do-gia-dung-lam-dep'),
    ('tineco', 'do-gia-dung-lam-dep'),
    ('dyson', 'do-gia-dung-lam-dep'),
    ('roborock', 'do-gia-dung-lam-dep'),
    ('wanbo', 'do-gia-dung-lam-dep');

-- Một hãng có thể có nhiều nhánh gợi ý; các bộ lọc vẫn dựa vào khóa brand_id thật của sản phẩm.
INSERT INTO brand_categories (brand_id, category_id)
SELECT brand.id, category.id
FROM stockflow_catalog_brand_link_seed seed
JOIN stockflow_catalog_brand_seed brand_seed
    ON brand_seed.slug = seed.brand_slug
JOIN brands brand
    ON brand.slug = brand_seed.slug
    OR LOWER(brand.name) = LOWER(brand_seed.name)
JOIN stockflow_catalog_category_seed category_seed
    ON category_seed.slug = seed.category_slug
JOIN categories category
    ON category.slug = category_seed.slug
    OR category.name = category_seed.name
WHERE NOT EXISTS (
    SELECT 1
    FROM brand_categories existing
    WHERE existing.brand_id = brand.id
      AND existing.category_id = category.id
);

DROP TABLE stockflow_catalog_brand_link_seed;
DROP TABLE stockflow_catalog_brand_seed;
DROP TABLE stockflow_catalog_category_seed;

