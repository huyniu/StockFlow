# Đối chiếu StockFlow với tài liệu bảo vệ CTKM118

Ngày kiểm tra: **13/10/2026**. Mã nguồn: **`c33ab9f`** và workspace hiện tại.

Nguồn yêu cầu: [Câu hỏi làm rõ môn học.pdf](https://drive.google.com/file/d/1MtiOh7NyiVDkgjvuIAaESq1DIsntubpf/view), trong [thư mục người dùng cung cấp](https://drive.google.com/drive/folders/1mrmuUInn9fA2hBmtRu4RY4tSPwwhB1p9). Thư mục trả về một file PDF gồm 13 trang.

## Kết luận dễ hiểu

StockFlow đã có nền tảng backend và nghiệp vụ để trả lời nhiều câu hỏi: API, đăng nhập/phân quyền, đặt hàng, kho, chống đặt trùng, xử lý đồng thời và kiểm thử.

**Điểm thiếu rõ nhất so với khung chấm là Angular.** Các phần tiếp theo cần bổ sung chủ yếu là minh chứng bảo vệ, kiểm thử tải và khả năng theo dõi/xử lý lỗi tích hợp. Có một lỗi tiếng Việt trong phản hồi bảo mật đã tái hiện được ở localhost.

PDF là ngân hàng câu hỏi phản biện và khung điểm gợi ý, không phải bản đặc tả bắt buộc triển khai mọi chức năng. Các câu 61–68 là tình huống nâng cao, không mặc định đồ án đã phân tán; câu 77–84 và ba tình huống cuối dùng phân loại xuất sắc. Chưa có đề cương/giao nhiệm vụ riêng của nhóm để xác nhận điều kiện bắt buộc hoặc dự đoán điểm.

## Các việc nên ưu tiên

### 1. Angular chưa được triển khai

- Liên quan: mục chấm Angular và tích hợp Client–Server **5/100 điểm**; câu 45–52, trang 7–8.
- Hiện có: giao diện HTML/CSS và JavaScript thuần, chia Native ES Modules. [app.js](../src/main/resources/static/app.js:1) nhập các module `.js`; không tìm thấy `angular.json`, ứng dụng Angular, `@angular` hoặc Angular `HttpClient` trong nguồn ứng dụng.
- [Kế hoạch Angular](angular-migration-plan.md:5) ghi rõ chưa triển khai; đây chưa phải bằng chứng có Angular.
- Cần làm nếu nhóm phải dùng Angular: một luồng thật có Component, Service/HttpClient, DTO TypeScript, routing, loading/error và gọi backend hiện có. Để đáp ứng đầy đủ phần frontend, tiếp tục chuyển các luồng cần trình diễn; một trang Angular chỉ chạy dữ liệu giả chưa đủ.
- Có thể bắt đầu bằng danh sách/chi tiết sản phẩm, rồi auth và checkout; giữ backend quyết định giá, tồn, quyền và trạng thái đơn.

### 2. Cần bộ minh chứng kiến trúc và demo API

- Liên quan: câu 01–04, 12–20, 40, 63, 71, 84; TH1.
- Hiện tại: một ứng dụng Spring Boot, một database, chia module nghiệp vụ. [pom.xml](../pom.xml), [Dockerfile](../Dockerfile) và các controller/service thể hiện mô hình **monolith chia module**. Có REST API công bố và các adapter gọi dịch vụ ngoài.
- Chưa đủ căn cứ gọi toàn bộ hệ thống là microservices hay SOA hoàn chỉnh chỉ vì có REST và `@Service`.
- Cần bổ sung sơ đồ kiến trúc thực tế, ranh giới/trách nhiệm dữ liệu của catalog–đơn hàng–kho–thanh toán–thông báo, và sơ đồ đặt hàng thành công/thất bại.
- API đã gọi được bằng client Node độc lập với giao diện trong lượt này. Cần đóng gói minh chứng đó thành collection Postman/Newman: login → đọc catalog → tạo đơn → gửi lại cùng khóa → xem đơn; thêm tình huống sai dữ liệu, thiếu token, sai quyền và truy cập đơn của người khác.
- Có Swagger/OpenAPI và kiểm thử API sẵn; chưa tìm thấy collection Postman, environment mẫu hoặc Newman trong repository. Đây là thiếu hiện vật dễ trình diễn, không phải backend chưa có API.
- Không cần tách toàn bộ hệ thống thành nhiều tiến trình để trả lời các câu này. Cần trình bày trung thực kiến trúc hiện tại và phương án tách một năng lực nghiệp vụ nếu đề bài yêu cầu.

### 3. Chưa có kết quả kiểm thử tải

- Liên quan: câu 05, 73, 81; TH3.
- Đã có kiểm thử tranh chấp: nhiều người mua, hai lượt thanh toán, hết hạn giữ hàng và thanh toán, đặt cùng khóa đồng thời. [OrderIntegrationTest](../src/test/java/com/stockflow/order/OrderIntegrationTest.java:216), [OrderIdempotencyIntegrationTest](../src/test/java/com/stockflow/order/OrderIdempotencyIntegrationTest.java:224), [StockflowPostgresIT](../src/test/java/com/stockflow/persistence/StockflowPostgresIT.java:199).
- Đây là bằng chứng tính đúng đắn; chưa chứng minh năng lực phục vụ 1.000 request đồng thời hoặc 100.000 người dùng.
- Chưa tìm thấy k6/JMeter, kịch bản tải, kết quả throughput, p95/p99, error rate hoặc giới hạn connection/thread pool được đo theo tải.
- [Báo cáo SQL](sql-optimization-report.md:3) có EXPLAIN và phép đo truy vấn trên môi trường kiểm chứng, không thay thế load test toàn hệ thống.
- Cần chạy trên database thử nghiệm, tăng tải từng bước, ghi cấu hình máy/dữ liệu và điều kiện đạt. Không chạy tải lớn vào Render/Aiven đang vận hành chỉ để chuẩn bị bảo vệ.

### 4. Theo dõi lỗi và hồi phục dịch vụ ngoài còn một phần

- Liên quan: câu 07, 43, 61–68, 74, 76; TH2.
- Đã có timeout cho GHN, Google, Gemini và Brevo; Gemini có fallback, GHN phân biệt mô phỏng/production. Đây là minh chứng tốt cho dịch vụ phụ thuộc bị lỗi.
- Email đơn hàng dùng outbox bền vững, worker gửi sau transaction và retry tối đa 5 lần với thời gian chờ; không cần thêm Kafka chỉ để trả lời câu email bất đồng bộ. [OrderNotificationService](../src/main/java/com/stockflow/order/service/OrderNotificationService.java), [OrderMailDispatcher](../src/main/java/com/stockflow/order/service/OrderMailDispatcher.java).
- Chưa thấy correlation/request ID xuyên request và log, tracing/metrics p95/p99 hoặc dashboard giám sát. Handler lỗi tổng quát trả thông báo an toàn nhưng chưa ghi exception kèm mã truy vết. [GlobalExceptionHandler](../src/main/java/com/stockflow/common/exception/GlobalExceptionHandler.java:106).
- Chưa có circuit breaker; có thể trình bày giới hạn hiện tại và bổ sung khi phù hợp, không coi đây là bắt buộc cho mọi API.
- VNPay đã kiểm chữ ký, số tiền và chống callback lặp. Tuy nhiên `REFUNDED` hiện là trạng thái trong database; chưa thấy API hoàn tiền/đối soát VNPay thực sự. Không mô tả rollback database như cách hoàn tác tiền đã trừ bên ngoài. [VNPayService](../src/main/java/com/stockflow/payment/service/VNPayService.java:128), [OrderService](../src/main/java/com/stockflow/order/service/OrderService.java:389).
- GHN có mã tham chiếu khách hàng và ghi nhận dispatch, nhưng gọi đối tác trong transaction. Cần kế hoạch tra cứu/đối soát khi đối tác đã tạo vận đơn mà phía ứng dụng chưa lưu được kết quả. [GhnShippingService](../src/main/java/com/stockflow/shipping/service/GhnShippingService.java:35).
- Outbox email có lease ngăn hai worker gửi đồng thời. Vẫn có cửa sổ gửi trùng nếu nhà cung cấp đã nhận email nhưng ứng dụng chưa ghi `sent_at`; không khẳng định bảo đảm gửi đúng một lần tuyệt đối.

### 5. Sửa lỗi tiếng Việt và cập nhật contract còn cũ

- Liên quan: câu 09–10, 25, 43, 52, 70.
- Lỗi đã tái hiện: `GET http://localhost:8080/api/v1/orders/my` không có token trả **401**, nhưng header là `application/json;charset=ISO-8859-1`; JSON đọc dưới UTF-8 bị hỏng câu thông báo tiếng Việt.
- Nguồn lỗi: [SecurityConfig.writeSecurityError](../src/main/java/com/stockflow/common/config/SecurityConfig.java:136) dùng `response.getWriter()` mà chưa chọn UTF-8. Cần đặt UTF-8 trước khi lấy writer hoặc ghi JSON bằng output stream; kiểm thử cả nội dung và encoding của 401/403. Quyết định từ chối quyền vẫn đúng.
- Một số mô tả README/OpenAPI vẫn ghi miễn phí giao hàng hoặc chỉ thanh toán/giao hàng mô phỏng, trong khi nguồn hiện có ShippingQuote/GHN, VNPay, COD, review và yêu cầu đổi trả. Ví dụ [OrderController](../src/main/java/com/stockflow/order/api/OrderController.java:55), [README](../README.md).
- Có version `/api/v1` và test OpenAPI/DTO; chưa thấy quy trình contract độc lập/snapshot và kế hoạch ngừng version cũ. Cần ghi rõ thay đổi tương thích và bộ request mẫu.
- `GET /products?page=-1` hiện trả 200 và chuẩn hóa về trang đầu. Không tự xem đây là lỗi nghiệp vụ; cần nêu rõ cách chuẩn hóa của endpoint, tránh hứa mọi tham số sai đều trả 400.

### 6. Chuẩn bị phần lý thuyết và đóng góp cá nhân

- Khung điểm gợi ý dành **20/100** cho kiến thức SOA/REST/SOAP/WSDL và **20/100** cho hiểu nguồn/đóng góp cá nhân/nhóm.
- Chưa thấy SOAP adapter, WSDL/XSD hoặc SoapUI project trong mã nguồn. Phần SOAP của PDF hỏi kiến thức và tình huống tích hợp; chưa đủ cơ sở kết luận web bắt buộc phải thêm SOAP.
- Cần chuẩn bị ví dụ SOAP Envelope/Fault, một WSDL/XSD mẫu và sơ đồ REST → adapter SOAP → đối tác; nếu giao nhiệm vụ riêng yêu cầu tích hợp SOAP thực thì mới triển khai adapter và kiểm thử.
- Cần bảng phân công/đóng góp thực tế và khả năng giải thích một request từ UI qua security/controller/service đến database. Chưa kiểm chứng tác giả hoặc mức đóng góp từng thành viên trong lượt này.

## Những phần dự án đã có

### REST, Spring Boot và dữ liệu

- REST API `/api/v1`, Swagger/OpenAPI có JWT bearer, DTO và phân trang catalog; backend nhận request mà không cần thao tác trên website. Câu 04, 09–10, 12, 21–28, 37–44, 84.
- Controller/service/repository, constructor injection, Bean Validation, exception handler, migration Flyway và transaction. Câu 37–44.
- Anti-oversell bằng UPDATE tồn kho có điều kiện, optimistic version và khóa đơn; đặt hàng thất bại rollback tồn/ledger/khóa yêu cầu. Câu 05–06, 11, 41–42, 77–78.
- Idempotency-Key theo khách hàng; cùng khóa/nội dung trả lại cùng đơn, đổi nội dung trả 409. Câu 06, 65.
- Bộ test PostgreSQL thật đã có trong CI/profile `postgres-tests`, nhưng không chạy lại bộ này trong lượt kiểm tra này.

### Bảo mật và sau bán hàng

- BCrypt, JWT, trạng thái ACTIVE, xác thực email, role và `auth_version`; JWT filter nạp role hiện tại từ database. Câu 53–57.
- Chủ sở hữu đơn và phạm vi kho nhân viên được kiểm tra ở backend, không chỉ ẩn nút ở UI. Test quyền/ownership có sẵn. Câu 08, 51, 53, 57.
- Upload ảnh đổi trả có giới hạn dung lượng/số ảnh, kiểm định dữ liệu ảnh và quyền truy cập; ảnh lưu database. Câu 60. Không vì catalog hiện dùng URL ảnh mà kết luận toàn dự án chưa có upload.
- Frontend/backend đang cùng origin; thiếu cấu hình CORS cho origin khác không phải lỗi hiện tại. Client Angular local khác port có thể dùng proxy hoặc cấu hình origin cụ thể khi triển khai. Câu 58.

### Tích hợp và triển khai

- Đã có VNPay/IPN, GHN, Google login, mail SMTP/Brevo và Gemini. Mức độ hoạt động live phụ thuộc cấu hình môi trường; có code không đồng nghĩa Render đã cấu hình đúng.
- Có Maven Wrapper, Dockerfile/Compose, `.env.example`, profiles, CI H2 + PostgreSQL và đóng gói JAR. Câu 44, 69, 75.
- Có script kiểm tra UI và nhiều test tự động. Không được suy kết quả test fixture thành bảo đảm dịch vụ bên ngoài đã hoạt động trên Render.

## Kiểm chứng thực hiện trong lượt này

1. Đọc toàn bộ văn bản PDF 13 trang; đối chiếu các nhóm câu hỏi với nguồn ứng dụng, test, scripts, dependency/build/CI và tài liệu hiện có.
2. Gọi localhost bằng Node, độc lập với giao diện: health **200/UP**; OpenAPI **200**, **75 đường dẫn** và bearer auth; catalog phân trang **200**, đủ metadata; orders/my và inventories thiếu token đều **401**, không có trường stack trace.
3. Tái hiện lỗi encoding của phản hồi 401; không lấy lỗi hiển thị terminal làm bằng chứng duy nhất: đã kiểm header ISO-8859-1 và ký tự Unicode replacement trong JSON đọc bằng client UTF-8.
4. Chạy **93 test PASS**, **0 failures/errors/skipped**, ở 10 lớp test sẵn có: AuthIntegrationTest (13), UserRoleAdministrationIntegrationTest (8), OrderIdempotencyIntegrationTest (16), OrderIntegrationTest (21), VNPayIpnIntegrationTest (8), BrevoOrderMailIntegrationTest (5), GhnClientTest (4), GeminiChatServiceTest (5), OpenApiIntegrationTest (3), PostPurchaseIntegrationTest (10).

Lệnh đã chạy:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' '-Dtest=AuthIntegrationTest,UserRoleAdministrationIntegrationTest,OrderIdempotencyIntegrationTest,OrderIntegrationTest,VNPayIpnIntegrationTest,BrevoOrderMailIntegrationTest,GhnClientTest,GeminiChatServiceTest,OpenApiIntegrationTest,PostPurchaseIntegrationTest' test
```

Log kiểm tra: `target/course-requirements-tests.log`; XML: `target/surefire-reports/`. Đây là bộ test chọn lọc, không phải kết quả chạy lại toàn bộ test của dự án. Dùng database test H2 và mock dịch vụ ngoài; không thực hiện giao dịch trên database vận hành.

Chưa thực hiện trong lượt này: load test, PostgreSQL/Testcontainers suite, kiểm tra CI đang chạy trên GitHub, xác minh biến môi trường/database/dịch vụ ngoài của Render, demo thanh toán/vận chuyển thật, đánh giá kiến thức và đóng góp của từng thành viên.

## Thứ tự hoàn thiện đề xuất

1. Chốt Angular có bắt buộc trong giao nhiệm vụ nhóm; triển khai theo kế hoạch nếu bắt buộc.
2. Sửa encoding 401/403 và cập nhật các mô tả API/tài liệu còn cũ.
3. Chuẩn bị sơ đồ kiến trúc/sequence, collection demo API và phần giải thích nguồn/đóng góp.
4. Bổ sung kịch bản tải, kết quả đo và request ID/log truy vết.
5. Hoàn thiện minh chứng xử lý lỗi đối tác/đối soát; chuẩn bị SOAP/WSDL và các tình huống nâng cao ở mức đề cương yêu cầu.

Lượt này chỉ tạo bản đối chiếu. Mã nguồn nghiệp vụ, database, JWT secret và cấu hình Render không thay đổi; không commit/push/deploy.
