-- Hoàn thiện ba nhóm menu theo ảnh tham khảo và lưu logo của hãng; không tạo sản phẩm/tồn/đơn mẫu.
-- V1–V12 giữ nguyên; bảng seed chỉ dùng trong migration, hãng và danh mục cũ giữ ID/tên/cây hiện có.
ALTER TABLE brands
    ADD COLUMN logo_url VARCHAR(2048);

CREATE TABLE stockflow_catalog_v13_categories (
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL PRIMARY KEY,
    parent_slug VARCHAR(180),
    depth INTEGER NOT NULL
);

INSERT INTO stockflow_catalog_v13_categories (name, slug, parent_slug, depth)
VALUES
    ('Phụ kiện', 'phu-kien', NULL, 1),
    ('Tivi, Điện máy', 'tivi-dien-may', NULL, 1),
    ('Hàng cũ', 'hang-cu', NULL, 1),
    ('Tivi', 'tivi', 'tivi-dien-may', 2),
    ('Tủ lạnh', 'tu-lanh', 'tivi-dien-may', 2),
    ('Tủ đông', 'tu-dong', 'tivi-dien-may', 2),
    ('Máy giặt', 'may-giat', 'tivi-dien-may', 2),
    ('Máy sấy quần áo', 'may-say-quan-ao', 'tivi-dien-may', 2),
    ('Máy rửa chén bát', 'may-rua-chen-bat', 'tivi-dien-may', 2),
    ('Máy lạnh', 'may-lanh', 'tivi-dien-may', 2),
    ('Giá treo tivi', 'gia-treo-tivi', 'tivi-dien-may', 2),
    ('Tủ chăm sóc quần áo', 'tu-cham-soc-quan-ao', 'tivi-dien-may', 2),
    ('Tivi di động', 'tivi-di-dong', 'tivi', 3),
    ('Phụ kiện di động', 'phu-kien-di-dong', 'phu-kien', 2),
    ('Phụ kiện laptop', 'phu-kien-laptop', 'phu-kien', 2),
    ('Thiết bị mạng', 'thiet-bi-mang', 'phu-kien', 2),
    ('Thiết bị lưu trữ', 'thiet-bi-luu-tru', 'phu-kien', 2),
    ('Phụ kiện khác', 'phu-kien-khac', 'phu-kien', 2),
    ('Dán màn hình', 'dan-man-hinh', 'phu-kien-di-dong', 3),
    ('Ốp lưng, Bao da', 'op-lung-bao-da', 'phu-kien-di-dong', 3),
    ('SIM 4G, 5G', 'sim-4g-5g', 'phu-kien-di-dong', 3),
    ('Cáp, Sạc', 'cap-sac', 'phu-kien-di-dong', 3),
    ('Sạc dự phòng', 'sac-du-phong', 'phu-kien-di-dong', 3),
    ('Trạm sạc dự phòng', 'tram-sac-du-phong', 'phu-kien-di-dong', 3),
    ('Dây đeo chéo điện thoại', 'day-deo-cheo-dien-thoai', 'phu-kien-di-dong', 3),
    ('Phụ kiện điện thoại', 'phu-kien-dien-thoai', 'phu-kien-di-dong', 3),
    ('Bảo hành mở rộng', 'bao-hanh-mo-rong', 'phu-kien-di-dong', 3),
    ('Chuột, bàn phím laptop', 'chuot-ban-phim-laptop', 'phu-kien-laptop', 3),
    ('Thiết bị phát sóng Wi-Fi', 'thiet-bi-phat-song-wifi', 'thiet-bi-mang', 3),
    ('Bộ phát Wi-Fi di động', 'bo-phat-wifi-di-dong', 'thiet-bi-mang', 3),
    ('Bộ kích sóng Wi-Fi', 'bo-kich-song-wifi', 'thiet-bi-mang', 3),
    ('Hub, Switch', 'hub-switch', 'thiet-bi-mang', 3),
    ('USB Wi-Fi', 'usb-wifi', 'thiet-bi-mang', 3),
    ('Card mạng', 'card-mang', 'thiet-bi-mang', 3),
    ('Thẻ nhớ', 'the-nho', 'thiet-bi-luu-tru', 3),
    ('USB', 'usb', 'thiet-bi-luu-tru', 3),
    ('Ổ cứng di động', 'o-cung-di-dong', 'thiet-bi-luu-tru', 3),
    ('Máy chơi game', 'may-choi-game', 'phu-kien-khac', 3),
    ('Máy chơi game cầm tay', 'may-choi-game-cam-tay', 'phu-kien-khac', 3),
    ('Quạt cầm tay, quạt mini', 'quat-cam-tay-quat-mini', 'phu-kien-khac', 3),
    ('Điện thoại cũ', 'dien-thoai-cu', 'hang-cu', 2),
    ('Máy tính bảng cũ', 'may-tinh-bang-cu', 'hang-cu', 2),
    ('Mac cũ', 'mac-cu', 'hang-cu', 2),
    ('Laptop cũ', 'laptop-cu', 'hang-cu', 2),
    ('Máy ảnh cũ', 'may-anh-cu', 'hang-cu', 2),
    ('Tai nghe cũ', 'tai-nghe-cu', 'hang-cu', 2),
    ('Loa cũ', 'loa-cu', 'hang-cu', 2),
    ('Đồng hồ thông minh cũ', 'dong-ho-thong-minh-cu', 'hang-cu', 2),
    ('Đồ gia dụng cũ', 'do-gia-dung-cu', 'hang-cu', 2),
    ('Màn hình cũ', 'man-hinh-cu', 'hang-cu', 2),
    ('Phụ kiện cũ', 'phu-kien-cu', 'hang-cu', 2),
    ('Tivi cũ', 'tivi-cu', 'hang-cu', 2),
    ('Sức khỏe, Làm đẹp cũ', 'suc-khoe-lam-dep-cu', 'hang-cu', 2),
    ('Camera giám sát hàng trưng bày', 'camera-giam-sat-trung-bay', 'hang-cu', 2);

-- Cấp gốc: tránh trùng cả slug và tên khác hoa/thường của danh mục người dùng đã tạo.
INSERT INTO categories (name, slug, parent_id)
SELECT seed.name, seed.slug, NULL
FROM stockflow_catalog_v13_categories seed
WHERE seed.depth = 1
  AND NOT EXISTS (
      SELECT 1
      FROM categories existing
      WHERE existing.slug = seed.slug
         OR LOWER(existing.name) = LOWER(seed.name)
  );

-- Cấp 2: chỉ nối nhóm mới; giữ cây đã nhập và không thêm con nếu cha đã ở cấp thứ ba.
INSERT INTO categories (name, slug, parent_id)
SELECT seed.name, seed.slug, parent.id
FROM stockflow_catalog_v13_categories seed
JOIN stockflow_catalog_v13_categories parent_seed
    ON parent_seed.slug = seed.parent_slug
JOIN categories parent
    ON parent.id = (
        SELECT existing.id
        FROM categories existing
        WHERE existing.slug = parent_seed.slug
           OR LOWER(existing.name) = LOWER(parent_seed.name)
        ORDER BY
            CASE WHEN existing.slug = parent_seed.slug THEN 0 ELSE 1 END,
            existing.id
        LIMIT 1
    )
WHERE seed.depth = 2
  AND (
      parent.parent_id IS NULL
      OR EXISTS (
          SELECT 1
          FROM categories grandparent
          WHERE grandparent.id = parent.parent_id
            AND grandparent.parent_id IS NULL
      )
  )
  AND NOT EXISTS (
      SELECT 1
      FROM categories existing
      WHERE existing.slug = seed.slug
         OR LOWER(existing.name) = LOWER(seed.name)
  );

-- Cấp 3: chỉ nối nhóm mới; giữ cây đã nhập và không thêm con nếu cha đã ở cấp thứ ba.
INSERT INTO categories (name, slug, parent_id)
SELECT seed.name, seed.slug, parent.id
FROM stockflow_catalog_v13_categories seed
JOIN stockflow_catalog_v13_categories parent_seed
    ON parent_seed.slug = seed.parent_slug
JOIN categories parent
    ON parent.id = (
        SELECT existing.id
        FROM categories existing
        WHERE existing.slug = parent_seed.slug
           OR LOWER(existing.name) = LOWER(parent_seed.name)
        ORDER BY
            CASE WHEN existing.slug = parent_seed.slug THEN 0 ELSE 1 END,
            existing.id
        LIMIT 1
    )
WHERE seed.depth = 3
  AND (
      parent.parent_id IS NULL
      OR EXISTS (
          SELECT 1
          FROM categories grandparent
          WHERE grandparent.id = parent.parent_id
            AND grandparent.parent_id IS NULL
      )
  )
  AND NOT EXISTS (
      SELECT 1
      FROM categories existing
      WHERE existing.slug = seed.slug
         OR LOWER(existing.name) = LOWER(seed.name)
  );

CREATE TABLE stockflow_catalog_v13_brands (
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL PRIMARY KEY
);

INSERT INTO stockflow_catalog_v13_brands (name, slug)
VALUES
    ('coocaa', 'coocaa'),
    ('TCL', 'tcl'),
    ('VSP', 'vsp'),
    ('Daikin', 'daikin'),
    ('Casper', 'casper'),
    ('Hitachi', 'hitachi'),
    ('Bosch', 'bosch'),
    ('OnePlus', 'oneplus');

-- Bổ sung hãng còn thiếu theo ảnh; logo để trống cho ADMIN nhập đúng ảnh nhận diện.
INSERT INTO brands (name, slug)
SELECT seed.name, seed.slug
FROM stockflow_catalog_v13_brands seed
WHERE NOT EXISTS (
    SELECT 1
    FROM brands existing
    WHERE existing.slug = seed.slug
       OR LOWER(existing.name) = LOWER(seed.name)
);

CREATE TABLE stockflow_catalog_v13_brand_categories (
    category_slug VARCHAR(180) NOT NULL,
    brand_slug VARCHAR(180) NOT NULL,
    PRIMARY KEY (category_slug, brand_slug)
);

INSERT INTO stockflow_catalog_v13_brand_categories (category_slug, brand_slug)
VALUES
    ('tivi', 'samsung'),
    ('tivi', 'lg'),
    ('tivi', 'xiaomi'),
    ('tivi', 'coocaa'),
    ('tivi', 'sony'),
    ('tivi', 'tcl'),
    ('tivi', 'vsp'),
    ('tivi', 'aqua'),
    ('tu-lanh', 'lg'),
    ('tu-lanh', 'samsung'),
    ('tu-lanh', 'xiaomi'),
    ('tu-lanh', 'panasonic'),
    ('tu-lanh', 'aqua'),
    ('tu-lanh', 'toshiba'),
    ('tu-dong', 'toshiba'),
    ('may-giat', 'lg'),
    ('may-giat', 'samsung'),
    ('may-giat', 'xiaomi'),
    ('may-giat', 'panasonic'),
    ('may-giat', 'aqua'),
    ('may-giat', 'toshiba'),
    ('may-say-quan-ao', 'lg'),
    ('may-say-quan-ao', 'samsung'),
    ('may-say-quan-ao', 'panasonic'),
    ('may-say-quan-ao', 'aqua'),
    ('may-say-quan-ao', 'toshiba'),
    ('may-rua-chen-bat', 'bosch'),
    ('may-lanh', 'panasonic'),
    ('may-lanh', 'daikin'),
    ('may-lanh', 'sharp'),
    ('may-lanh', 'lg'),
    ('may-lanh', 'aqua'),
    ('may-lanh', 'samsung'),
    ('may-lanh', 'casper'),
    ('may-lanh', 'tcl'),
    ('may-lanh', 'hitachi'),
    ('may-lanh', 'xiaomi'),
    ('tu-cham-soc-quan-ao', 'lg'),
    ('tu-cham-soc-quan-ao', 'samsung'),
    ('dan-man-hinh', 'apple'),
    ('dan-man-hinh', 'samsung'),
    ('dan-man-hinh', 'xiaomi'),
    ('dan-man-hinh', 'oppo'),
    ('op-lung-bao-da', 'apple'),
    ('op-lung-bao-da', 'samsung'),
    ('op-lung-bao-da', 'xiaomi'),
    ('op-lung-bao-da', 'oppo'),
    ('op-lung-bao-da', 'huawei'),
    ('cap-sac', 'apple'),
    ('cap-sac', 'samsung'),
    ('cap-sac', 'anker'),
    ('cap-sac', 'xiaomi'),
    ('sac-du-phong', 'anker'),
    ('sac-du-phong', 'xiaomi'),
    ('sac-du-phong', 'samsung'),
    ('tram-sac-du-phong', 'anker'),
    ('phu-kien-dien-thoai', 'apple'),
    ('phu-kien-dien-thoai', 'samsung'),
    ('phu-kien-dien-thoai', 'oppo'),
    ('phu-kien-dien-thoai', 'xiaomi'),
    ('phu-kien-dien-thoai', 'huawei'),
    ('bao-hanh-mo-rong', 'apple'),
    ('bao-hanh-mo-rong', 'samsung'),
    ('thiet-bi-mang', 'tp-link'),
    ('thiet-bi-mang', 'asus'),
    ('thiet-bi-mang', 'xiaomi'),
    ('thiet-bi-luu-tru', 'samsung'),
    ('thiet-bi-luu-tru', 'sony'),
    ('may-choi-game', 'sony'),
    ('may-choi-game-cam-tay', 'asus'),
    ('quat-cam-tay-quat-mini', 'xiaomi'),
    ('quat-cam-tay-quat-mini', 'philips'),
    ('dien-thoai-cu', 'apple'),
    ('dien-thoai-cu', 'samsung'),
    ('dien-thoai-cu', 'oppo'),
    ('dien-thoai-cu', 'xiaomi'),
    ('dien-thoai-cu', 'tecno'),
    ('dien-thoai-cu', 'honor'),
    ('dien-thoai-cu', 'nubia'),
    ('dien-thoai-cu', 'sony'),
    ('dien-thoai-cu', 'nokia'),
    ('dien-thoai-cu', 'nothing'),
    ('dien-thoai-cu', 'masstel'),
    ('dien-thoai-cu', 'huawei'),
    ('dien-thoai-cu', 'meizu'),
    ('dien-thoai-cu', 'realme'),
    ('dien-thoai-cu', 'itel'),
    ('dien-thoai-cu', 'infinix'),
    ('dien-thoai-cu', 'asus'),
    ('dien-thoai-cu', 'tcl'),
    ('dien-thoai-cu', 'oneplus'),
    ('may-tinh-bang-cu', 'apple'),
    ('may-tinh-bang-cu', 'samsung'),
    ('may-tinh-bang-cu', 'xiaomi'),
    ('may-tinh-bang-cu', 'huawei'),
    ('may-tinh-bang-cu', 'oppo'),
    ('may-tinh-bang-cu', 'lenovo'),
    ('mac-cu', 'apple'),
    ('laptop-cu', 'apple'),
    ('laptop-cu', 'dell'),
    ('laptop-cu', 'asus'),
    ('laptop-cu', 'acer'),
    ('laptop-cu', 'hp'),
    ('laptop-cu', 'microsoft-surface'),
    ('laptop-cu', 'lenovo'),
    ('laptop-cu', 'msi'),
    ('laptop-cu', 'lg'),
    ('laptop-cu', 'gigabyte'),
    ('may-anh-cu', 'canon'),
    ('may-anh-cu', 'sony'),
    ('may-anh-cu', 'fujifilm'),
    ('may-anh-cu', 'dji'),
    ('may-anh-cu', 'gopro'),
    ('tai-nghe-cu', 'apple'),
    ('tai-nghe-cu', 'samsung'),
    ('tai-nghe-cu', 'sony'),
    ('tai-nghe-cu', 'jbl'),
    ('tai-nghe-cu', 'marshall'),
    ('tai-nghe-cu', 'bose'),
    ('tai-nghe-cu', 'sennheiser'),
    ('tai-nghe-cu', 'beats'),
    ('tai-nghe-cu', 'anker'),
    ('loa-cu', 'jbl'),
    ('loa-cu', 'marshall'),
    ('loa-cu', 'sony'),
    ('loa-cu', 'bose'),
    ('loa-cu', 'harman-kardon'),
    ('dong-ho-thong-minh-cu', 'apple'),
    ('dong-ho-thong-minh-cu', 'samsung'),
    ('dong-ho-thong-minh-cu', 'huawei'),
    ('dong-ho-thong-minh-cu', 'xiaomi'),
    ('dong-ho-thong-minh-cu', 'garmin'),
    ('dong-ho-thong-minh-cu', 'amazfit'),
    ('do-gia-dung-cu', 'philips'),
    ('do-gia-dung-cu', 'panasonic'),
    ('do-gia-dung-cu', 'xiaomi'),
    ('do-gia-dung-cu', 'sharp'),
    ('do-gia-dung-cu', 'sunhouse'),
    ('man-hinh-cu', 'samsung'),
    ('man-hinh-cu', 'lg'),
    ('man-hinh-cu', 'asus'),
    ('man-hinh-cu', 'msi'),
    ('phu-kien-cu', 'apple'),
    ('phu-kien-cu', 'samsung'),
    ('phu-kien-cu', 'anker'),
    ('tivi-cu', 'samsung'),
    ('tivi-cu', 'lg'),
    ('tivi-cu', 'xiaomi'),
    ('tivi-cu', 'coocaa'),
    ('tivi-cu', 'sony'),
    ('tivi-cu', 'tcl'),
    ('tivi-cu', 'vsp'),
    ('tivi-cu', 'aqua'),
    ('suc-khoe-lam-dep-cu', 'philips'),
    ('suc-khoe-lam-dep-cu', 'panasonic'),
    ('suc-khoe-lam-dep-cu', 'dyson'),
    ('suc-khoe-lam-dep-cu', 'xiaomi'),
    ('camera-giam-sat-trung-bay', 'imou'),
    ('camera-giam-sat-trung-bay', 'ezviz'),
    ('camera-giam-sat-trung-bay', 'tp-link'),
    ('camera-giam-sat-trung-bay', 'tiandy');

-- Dùng hãng chung cho hàng mới/hàng cũ; không biến Apple/Samsung hoặc model iPhone thành danh mục riêng.
INSERT INTO brand_categories (brand_id, category_id)
SELECT brand.id, category.id
FROM stockflow_catalog_v13_brand_categories relation
JOIN stockflow_catalog_v13_categories category_seed
    ON category_seed.slug = relation.category_slug
JOIN categories category
    ON category.id = (
        SELECT existing.id
        FROM categories existing
        WHERE existing.slug = category_seed.slug
           OR LOWER(existing.name) = LOWER(category_seed.name)
        ORDER BY
            CASE WHEN existing.slug = category_seed.slug THEN 0 ELSE 1 END,
            existing.id
        LIMIT 1
    )
LEFT JOIN stockflow_catalog_v13_brands brand_seed
    ON brand_seed.slug = relation.brand_slug
JOIN brands brand
    ON brand.id = (
        SELECT existing.id
        FROM brands existing
        WHERE existing.slug = relation.brand_slug
           OR (brand_seed.name IS NOT NULL AND LOWER(existing.name) = LOWER(brand_seed.name))
        ORDER BY
            CASE WHEN existing.slug = relation.brand_slug THEN 0 ELSE 1 END,
            existing.id
        LIMIT 1
    )
WHERE NOT EXISTS (
    SELECT 1
    FROM brand_categories existing
    WHERE existing.brand_id = brand.id
      AND existing.category_id = category.id
);

-- Dọn staging; database chỉ giữ danh mục, hãng và quan hệ được ứng dụng sử dụng.
DROP TABLE stockflow_catalog_v13_brand_categories;
DROP TABLE stockflow_catalog_v13_brands;
DROP TABLE stockflow_catalog_v13_categories;

