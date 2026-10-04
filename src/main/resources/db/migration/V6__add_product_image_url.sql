-- Lưu ảnh bìa cùng sản phẩm để quản trị viên không phải chỉnh bảng ảnh trong JavaScript.
-- Cột tùy chọn giữ tương thích dữ liệu cũ và cho phép giao diện dùng ảnh dự phòng.
ALTER TABLE products
    ADD COLUMN image_url VARCHAR(2048);
