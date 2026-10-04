-- Xóa cấu hình theo kiểu lưu trữ: giữ SKU, tồn kho, đơn hàng và sổ cái bất biến.
ALTER TABLE product_versions
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

-- Phân biệt màu đã xóa khỏi lựa chọn với màu chỉ đang ngừng bán để có thể khôi phục.
ALTER TABLE product_variants
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
