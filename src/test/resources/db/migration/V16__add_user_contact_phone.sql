-- H2 dùng cùng quy tắc số liên hệ với PostgreSQL; các tài khoản cũ tiếp tục có phone null.
ALTER TABLE users
    ADD COLUMN phone VARCHAR(30);

-- REGEXP_LIKE thay toán tử ~; không dùng chuỗi rỗng để biểu diễn số chưa có.
ALTER TABLE users
    ADD CONSTRAINT ck_users_phone
    CHECK (
        phone IS NULL
        OR REGEXP_LIKE(phone, '^[+]?[0-9]{8,15}$')
    );
