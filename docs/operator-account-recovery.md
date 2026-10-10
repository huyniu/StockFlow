# Khôi phục ba tài khoản vận hành

Khôi phục đúng các bản ghi `admin@stockflow.com` (ADMIN), `manager@stockflow.com` (MANAGER), `staff.hn@stockflow.com` (WAREHOUSE_STAFF). Không tạo tài khoản thay thế, đổi ID, role, phân công kho, đơn hàng hay lịch sử kho. V32 giữ nguyên; V33 chỉ thêm `operator_recovered_at` và bảng ghi nhận yêu cầu đã hoàn tất.

## Cơ chế

Mặc định `OPERATOR_RECOVERY_ENABLED=false`. Khi chủ hệ thống bật có chủ đích, runner khóa ba tài khoản, kiểm tra tất cả cấu hình trước khi thay đổi, rồi cập nhật trong cùng transaction: BCrypt mật khẩu mới, ACTIVE, thời điểm khôi phục và tăng `auth_version`. Email phải đã xác thực và role phải đúng; nếu không đúng, dừng để chủ hệ thống kiểm tra, không tự đổi quyền hay bỏ qua xác thực.

Mỗi yêu cầu có mã riêng được lưu trong database. Chạy lại mã đã hoàn tất không reset mật khẩu, không tăng version và không kích hoạt lại tài khoản, kể cả khi đã gỡ các mật khẩu khỏi cấu hình. Không xóa bản ghi trong `operator_recovery_runs` để chạy lại; khi chủ hệ thống thật sự muốn đổi mật khẩu lần nữa, dùng mã mới cùng ba mật khẩu mới.

Ba mật khẩu phải khác nhau, mới so với mật khẩu hiện tại, ít nhất 12 ký tự và tối đa 72 byte UTF-8. Các mật khẩu demo từng công khai bị từ chối. Không có endpoint HTTP khôi phục; không ghi mật khẩu vào code, Git, log, tham số dòng lệnh hoặc chat. Policy chỉ mở định danh vận hành sau khi có dấu khôi phục trong DB; login và JWT vẫn kiểm tra ACTIVE, email verified, quyền hiện tại và `auth_version`. Seeder giữ nguyên mật khẩu/trạng thái và phân công của tài khoản đã khôi phục.

## Localhost

1. Dừng ứng dụng đang chạy. Xác minh cấu hình datasource thực tế trong IntelliJ (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, hoặc override `SPRING_DATASOURCE_URL` và profile tương ứng) trỏ đúng database local muốn khôi phục. Mặc định application.yml dùng localhost:5433/stockflow; cấu hình chạy có thể override sang cổng khác. Không dùng database QA hoặc mặc nhiên cho rằng local là database Render. Sao lưu database theo quy trình của chủ hệ thống.
2. Đặt các biến sau trong cấu hình riêng của tiến trình chạy, chẳng hạn Environment variables của IntelliJ; không lưu cấu hình chứa mật khẩu vào project/Git. Giữ `JWT_SECRET` hiện tại và các biến datasource hiện tại:
   - `OPERATOR_RECOVERY_ENABLED=true`
   - `OPERATOR_RECOVERY_REQUEST_ID`: mã duy nhất, 1–100 ký tự chữ/số hoặc `._:-`, ví dụ `local-operators-20261010-01`.
   - `OPERATOR_RECOVERY_ADMIN_PASSWORD`: mật khẩu mới riêng của ADMIN.
   - `OPERATOR_RECOVERY_MANAGER_PASSWORD`: mật khẩu mới riêng của MANAGER.
   - `OPERATOR_RECOVERY_STAFF_PASSWORD`: mật khẩu mới riêng của nhân viên kho.
3. Chạy bản ứng dụng có V33. Giữ `app.demo.seed-catalog=false` nếu dùng profile demo với dữ liệu vận hành; không bật nạp catalog để khôi phục tài khoản. Log thành công chỉ ghi ba tài khoản được khôi phục và token cũ bị vô hiệu hóa, không ghi credential.
4. Đặt `OPERATOR_RECOVERY_ENABLED=false`, gỡ ba biến mật khẩu và mã yêu cầu khỏi cấu hình tiến trình, rồi khởi động lại. Đăng nhập ba tài khoản bằng mật khẩu mới; kiểm tra ADMIN quản trị user, MANAGER xem báo cáo, nhân viên kho chỉ thao tác kho được phân công. Mật khẩu demo cũ và JWT trước khôi phục phải bị từ chối.

Lượt triển khai này chưa khôi phục database local: tiến trình công cụ không có ba biến mật khẩu riêng hoặc mã yêu cầu. Không tự tạo mật khẩu thay chủ hệ thống và không lấy mật khẩu từ cấu hình IDE để đưa ra log.

## Render

Chưa cập nhật, deploy hay khôi phục database Render trong lượt này. Chủ hệ thống cần thực hiện các bước sau khi đã duyệt bản sửa:

1. Xác minh service và datasource Render trỏ đúng database; kiểm tra ba tài khoản tồn tại, đúng role và đã xác thực email. Sao lưu theo quy trình hiện có. Database local và Render có thể khác nhau: khôi phục local không khôi phục Render.
2. Đưa bản sửa lên Render theo quy trình triển khai đã duyệt. Giữ V32 đã áp dụng; Flyway chạy V33 mới. Không đổi `JWT_SECRET`, không dùng Flyway clean, không mở lại thanh demo.
3. Trong Environment riêng của service, đặt cùng năm biến ở phần localhost, dùng mã riêng cho Render và ba mật khẩu do chủ hệ thống chọn. Không đưa mật khẩu vào lệnh Shell hoặc build log. Giữ các biến datasource và secret hiện tại. Nếu profile demo hoạt động, giữ `DEMO_SEED_CATALOG=false`.
4. Khởi động bản sửa với cấu hình khôi phục. Kiểm tra log thành công và truy vấn an toàn dưới đây. Nếu báo thiếu user/sai role/chưa verified, không sửa role hoặc verified tùy tiện; xác minh đúng database trước.
5. Tắt biến enabled, xóa ba biến mật khẩu và mã yêu cầu, khởi động lại service. Kiểm tra đăng nhập/quyền ba tài khoản, từ chối mật khẩu demo và token cũ, và xác minh snapshot không đổi sau restart. Nếu còn instance cũ, thay toàn bộ bằng bản sửa trước khi chốt kết quả.

Chỉ dùng SELECT không chứa password hash để kiểm tra trước/sau trên từng database:

```sql
SELECT u.id, u.email, r.name AS role, u.status, u.email_verified,
       u.auth_version, u.operator_recovered_at
FROM users u JOIN roles r ON r.id = u.role_id
WHERE LOWER(u.email) IN ('admin@stockflow.com', 'manager@stockflow.com', 'staff.hn@stockflow.com')
ORDER BY u.email;
SELECT request_id, completed_at FROM operator_recovery_runs ORDER BY completed_at;
```

Sau khôi phục ID/role phải giữ nguyên, ACTIVE và version tăng đúng một so với trước; sau restart version và thời điểm khôi phục không đổi. Kiểm tra phân công/đơn/ledger theo snapshot nội bộ, không xuất dữ liệu khách hoặc credential vào log.

## Kiểm chứng tại workspace ngày 10/10/2026

- Bộ liên quan: 38/38 PASS (`operator-recovery-focused.log`).
- Maven `test package`: 837/837 PASS, không failures/errors/skipped; JAR đóng gói thành công (`target/operator-recovery-full.log`). `git diff --check` PASS.
- Test dùng H2 riêng: mật khẩu mới, từ chối demo cũ, role ADMIN/MANAGER/WAREHOUSE_STAFF, kho được phân công, token cũ và trạng thái INACTIVE; giữ ID/đơn/ledger/phân công; gọi lại startup runner/seeder và replay cùng request không đổi tài khoản; hai yêu cầu đồng thời cùng mã chỉ thực hiện một lần; cấu hình không hợp lệ không thay đổi một phần.
- V31/V32/V33 main và test được đối chiếu giống nhau; V33 không tự khôi phục tài khoản. Chưa kiểm chứng V33 trên PostgreSQL thật trong lượt này, chưa đăng nhập/restart ứng dụng vận hành local hoặc Render. Không commit/push/deploy hoặc đổi JWT_SECRET.

## Tiếp nối: tạo tài khoản mới theo yêu cầu chủ hệ thống

Chủ hệ thống chọn tạo mới thay vì cung cấp mật khẩu để khôi phục. Đã tạo trực tiếp trên database local `localhost:5432/stockflow` ba tài khoản ACTIVE, xác thực bởi thao tác của chủ hệ thống, mật khẩu BCrypt ngẫu nhiên riêng:

- `admin.owner@stockflow.com`: ADMIN, ID 8.
- `manager.owner@stockflow.com`: MANAGER, ID 9.
- `staff.hn.owner@stockflow.com`: WAREHOUSE_STAFF, ID 10; sao chép một phân công kho hiện có từ tài khoản nhân viên cũ.

Mật khẩu chỉ lưu trong `.idea/operator-new-accounts.json`, bị Git bỏ qua và giới hạn ACL cho tài khoản Windows Admin cùng tiến trình sandbox tạo file; không ghi vào tài liệu, source, chat hay log. Không chạy lại thao tác tạo để reset; script một lần trong target từ chối nếu file/tài khoản đã tồn tại.

Đã đối chiếu snapshot trong transaction: toàn bộ tài khoản cũ, đơn, tồn, movements và phân công cũ không đổi. Giữ 12 đơn và 70 movements. Không khôi phục ba tài khoản demo cũ; chúng vẫn INACTIVE.

Đã chạy ứng dụng kiểm chứng tạm trên loopback cổng 18081, tắt seeder, khôi phục, hết hạn đơn và gửi thông báo đơn; giữ JWT_SECRET hiện có. Flyway áp dụng V33 thành công trên PostgreSQL local 13.2. Cả ba tài khoản đăng nhập HTTP thành công, sai mật khẩu bị 401, quyền ADMIN/MANAGER/WAREHOUSE_STAFF đúng trên users/me, admin/users và reports/order-summary. Đã dừng tiến trình kiểm chứng; chủ hệ thống chạy lại ứng dụng trong IntelliJ để sử dụng. Render chưa tạo tài khoản, cập nhật hoặc deploy. Không commit/push/deploy.

Theo yêu cầu tiếp theo của chủ hệ thống, đã đổi email/mật khẩu của chính ba bản ghi mới, giữ ID 8/9/10 lần lượt ADMIN/MANAGER/WAREHOUSE_STAFF. Email hiện tại được chuẩn hóa thành `adminh@stockflow.com`, `managerh@stockflow.com`, `warehousestaff@stockflow.com`; form đăng nhập chấp nhận cách viết hoa do chủ hệ thống cung cấp. Mật khẩu do chủ hệ thống chỉ định chỉ lưu trong file riêng đã nêu, không đưa vào tài liệu/source/log. Mỗi `auth_version` tăng một để vô hiệu hóa phiên cũ; kiểm tra BCrypt xác nhận mật khẩu mới và từ chối mật khẩu trước. Snapshot tài khoản cũ/đơn/tồn/ledger/phân công không đổi. Render vẫn chưa thay đổi.
