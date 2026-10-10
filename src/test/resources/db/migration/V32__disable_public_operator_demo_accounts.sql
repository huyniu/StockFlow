-- Giữ user/ledger lịch sử nhưng vô hiệu hóa các credential quản trị đã công khai.
UPDATE users SET status = 'INACTIVE', auth_version = auth_version + 1
WHERE LOWER(email) IN ('admin@stockflow.com', 'manager@stockflow.com', 'staff.hn@stockflow.com')
   OR (LOWER(email) = 'customer@stockflow.com'
       AND role_id <> (SELECT id FROM roles WHERE name = 'CUSTOMER'));
