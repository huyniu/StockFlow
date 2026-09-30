<!-- Báo cáo nghiệm thu Milestone 6 và danh sách file thay đổi, ghi bằng tiếng Việt UTF-8. -->
# StockFlow — nghiệm thu Portfolio Finish

Ngày kiểm chứng: **30/09/2026**. Môi trường: Windows, Java 17, PostgreSQL 13.2.

## Kết quả triển khai

- SpringDoc **2.8.5**, OpenAPI **StockFlow API / 1.0**, Bearer JWT qua nút Authorize.
- Swagger và tài liệu OpenAPI public; role/ownership của API nghiệp vụ giữ nguyên.
- Annotation Tag/Operation cho 9 controller; Pageable được mô tả bằng page/size/sort, không lộ principal.
- Seed chỉ bật trong profile **demo**: 4 tài khoản BCrypt, 3 kho, 4 danh mục, 24 sản phẩm.
- 72 inventory ban đầu được nhập qua InventoryService, tạo 72 GOODS_RECEIPT với actor ADMIN và snapshot hợp lệ.
- Nhân viên staff.hn được gán WH-HAN-01; seed không ghi đè mật khẩu, giá hoặc tồn kho đã có.
- Dockerfile nhiều giai đoạn với Java 17, Compose chạy ứng dụng và PostgreSQL sau health check.
- CI cho push/pull_request vào main: Ubuntu, Temurin 17, Maven Wrapper test, package và upload JAR.
- README tiếng Anh có architecture, ERD, hướng dẫn chạy và demo, phân quyền, số liệu SQL dẫn về báo cáo Milestone 5.

## Maven test và package

Đã chạy trực tiếp **.\mvnw.cmd test** và **.\mvnw.cmd package**.
MAVEN_OPTS được bổ sung tạm thời
`-Dmaven.repo.local=C:/Users/Admin/.m2/repository`, rồi khôi phục sau mỗi lệnh.

- **test: BUILD SUCCESS — 80 tests, 0 failures, 0 errors, 0 skipped**, tổng thời gian 18.343 giây.
- **package: BUILD SUCCESS**, chạy lại cả 80 test và tạo JAR, tổng thời gian 21.344 giây.
- JAR: `target/stockflow-0.0.1-SNAPSHOT.jar` (**65.401.606 bytes**).
- 11 test mới: OpenApiIntegrationTest (3), DemoDataIntegrationTest (8).
- Test demo dùng H2 riêng, rollback từng thay đổi nghiệp vụ và không xóa ledger.
- Test sẵn có vẫn kiểm chứng tranh mua đồng thời, rollback, thanh toán/hủy/hết hạn, phân quyền và báo cáo.

Log build ở `target/milestone6-test.log` và `target/milestone6-package.log`, không đưa vào Git.
Dòng ERROR do ghi chú vượt 255 ký tự thuộc test cố tình gây lỗi để kiểm tra rollback, không phải test thất bại.
Flyway có cảnh báo tương thích H2 2.3; toàn bộ migration/validation/test đã chạy thành công.

## Kiểm chứng JAR trên PostgreSQL

Dùng database tạm riêng, chạy JAR với profile demo tại cổng 8096; đã dừng tiến trình và xóa database tạm sau kiểm chứng.

1. Flyway V1–V5 và Hibernate schema validation thành công.
2. Health UP, Swagger UI HTTP 200, OpenAPI title/version và Bearer scheme đúng; catalog public có 24 sản phẩm.
3. Database ban đầu có đúng 3 kho, 4 danh mục, 24 sản phẩm, 72 inventory và 72 movement.
4. Cả ADMIN, MANAGER, WAREHOUSE_STAFF, CUSTOMER đăng nhập thật và gọi users/me được.
5. Nhân viên xem inventory Hà Nội được, kho Đà Nẵng bị 403; CUSTOMER/WAREHOUSE_STAFF bị 403 khi gọi báo cáo.
6. Tạo đơn 6 điện thoại: PENDING, giữ đúng 15 phút, thanh toán CONFIRMED; xác nhận lặp vẫn chỉ có 1 payment.
7. Tạo rồi hủy đơn 2 laptop: CANCELLED và trả lại tồn đã giữ.
8. Revenue/Top Products tính đúng 6 điện thoại và 35.940.000; sửa giá catalog không đổi doanh thu đã chụp.
9. Trigger PostgreSQL từ chối cả UPDATE và DELETE ledger qua SQL trực tiếp.
10. Khởi động JAR lần hai: vẫn 24 sản phẩm, 72 inventory, 76 movement sau hai luồng đơn, 1 payment và 1 phân công.
    Điện thoại Hà Nội vẫn tồn 0, giá đã sửa 123.45 giữ nguyên; tài khoản/đơn hàng không bị reset.

Log runtime ở `target/milestone6-postgres*.log`.

## Phần chưa chạy trên môi trường hiện tại

- **Chưa build/chạy container bằng Docker Compose** vì máy không có Docker CLI. Cú pháp YAML và cấu hình cốt lõi của Compose/CI đã được đọc, parse và kiểm tra thành công; JAR đã chạy thật trên PostgreSQL 13.2.
- **Chưa chạy workflow trên GitHub**; workflow kích hoạt sau khi thay đổi được push/PR vào main.
- Không có deploy công khai/video/frontend trong yêu cầu triển khai này. README ghi rõ phạm vi API hiện có và các extension fulfillment.

## Danh sách file thay đổi

**29 file: 11 file mới, 18 file sửa đổi.** Các file có comment/JavaDoc tiếng Việt có dấu.

### File mới

- [.dockerignore](../.dockerignore)
- [.gitattributes](../.gitattributes)
- [.github/workflows/ci.yml](../.github/workflows/ci.yml)
- [Dockerfile](../Dockerfile)
- [src/main/java/com/stockflow/common/config/OpenApiConfig.java](../src/main/java/com/stockflow/common/config/OpenApiConfig.java)
- [src/main/java/com/stockflow/demo/DemoDataRunner.java](../src/main/java/com/stockflow/demo/DemoDataRunner.java)
- [src/main/java/com/stockflow/demo/DemoDataSeeder.java](../src/main/java/com/stockflow/demo/DemoDataSeeder.java)
- [src/main/resources/application-demo.yml](../src/main/resources/application-demo.yml)
- [src/test/java/com/stockflow/common/config/OpenApiIntegrationTest.java](../src/test/java/com/stockflow/common/config/OpenApiIntegrationTest.java)
- [src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java](../src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java)
- [docs/milestone6-verification.md](../docs/milestone6-verification.md)

### File sửa đổi

- [.env.example](../.env.example)
- [README.md](../README.md)
- [compose.yaml](../compose.yaml)
- [pom.xml](../pom.xml)
- [src/main/java/com/stockflow/auth/api/AuthController.java](../src/main/java/com/stockflow/auth/api/AuthController.java)
- [src/main/java/com/stockflow/catalog/api/CategoryController.java](../src/main/java/com/stockflow/catalog/api/CategoryController.java)
- [src/main/java/com/stockflow/catalog/api/ProductController.java](../src/main/java/com/stockflow/catalog/api/ProductController.java)
- [src/main/java/com/stockflow/catalog/repository/CategoryRepository.java](../src/main/java/com/stockflow/catalog/repository/CategoryRepository.java)
- [src/main/java/com/stockflow/catalog/repository/ProductRepository.java](../src/main/java/com/stockflow/catalog/repository/ProductRepository.java)
- [src/main/java/com/stockflow/common/api/HealthController.java](../src/main/java/com/stockflow/common/api/HealthController.java)
- [src/main/java/com/stockflow/common/config/SecurityConfig.java](../src/main/java/com/stockflow/common/config/SecurityConfig.java)
- [src/main/java/com/stockflow/inventory/api/InventoryController.java](../src/main/java/com/stockflow/inventory/api/InventoryController.java)
- [src/main/java/com/stockflow/order/api/OrderController.java](../src/main/java/com/stockflow/order/api/OrderController.java)
- [src/main/java/com/stockflow/report/api/ReportController.java](../src/main/java/com/stockflow/report/api/ReportController.java)
- [src/main/java/com/stockflow/user/api/UserController.java](../src/main/java/com/stockflow/user/api/UserController.java)
- [src/main/java/com/stockflow/warehouse/api/WarehouseController.java](../src/main/java/com/stockflow/warehouse/api/WarehouseController.java)
- [src/main/java/com/stockflow/warehouse/repository/WarehouseRepository.java](../src/main/java/com/stockflow/warehouse/repository/WarehouseRepository.java)
- [src/main/resources/application.yml](../src/main/resources/application.yml)
