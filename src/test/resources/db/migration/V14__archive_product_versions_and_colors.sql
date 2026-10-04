-- H2 dùng cùng cờ lưu trữ; không xóa dữ liệu lịch sử khi ADMIN bỏ phiên bản hoặc màu.
ALTER TABLE product_versions
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE product_variants
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
