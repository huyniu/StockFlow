<!-- Tài liệu định hướng StockFlow Tech ngày 01/10/2026, phân biệt chức năng đã có và công việc dự kiến. -->
# Hướng phát triển dự án StockFlow Tech

> StockFlow Tech là website bán phụ kiện máy tính và thiết bị công nghệ của một cửa hàng sở hữu nhiều kho. Dự án đồng thời phục vụ bài tập lớn và portfolio ứng tuyển Java Backend Developer.
>
> Tài liệu này tổng hợp hướng phát triển tiếp theo. Các mục dự kiến chưa phải chức năng đã triển khai; quyết định nghiệp vụ đã chốt trong [ANTIGRAVITY_HANDOFF.md](ANTIGRAVITY_HANDOFF.md) tiếp tục được giữ nguyên.

## 1. Định vị sản phẩm

Một cửa hàng trực tiếp quản lý sản phẩm, giá bán và tồn kho tại Hà Nội, Đà Nẵng, TP.HCM. Khách mua hàng từ cửa hàng; hệ thống không có người bán bên thứ ba, gian hàng độc lập hoặc chia doanh thu giữa nhiều người bán.

Ngành hàng giai đoạn đầu gồm:

- **Bàn phím & Chuột:** bàn phím cơ, chuột văn phòng, chuột chơi game.
- **Tai nghe & Loa:** tai nghe có dây/không dây, tai nghe có micro, loa máy tính.
- **Webcam & Micro:** thiết bị phục vụ học tập, họp trực tuyến và làm việc.
- **Hub, Cáp & Bộ sạc:** hub USB, cáp kết nối, bộ sạc.
- **Màn hình & Phụ kiện bàn làm việc:** màn hình, giá đỡ, phụ kiện bố trí góc làm việc.

<!-- Quyết định V12: một thẻ model, chọn phiên bản và màu, giữ các SKU/lịch sử đã có. -->
Mỗi cấu hình bán là một SKU có giá và tồn kho riêng. V12 hiển thị một thẻ model, ví dụ Apple Watch SE 3; khách chọn phiên bản rồi màu trong cùng trang. ADMIN khai báo phiên bản tự do cho kích thước/GPS/dung lượng/RAM và bảng thông số riêng ghi đè bảng chung. Cùng màu ở hai phiên bản vẫn là hai SKU. Không tự gộp các sản phẩm đã nhập độc lập; serial/IMEI và bảo hành nằm ngoài phạm vi ban đầu. Xem [Model, phiên bản và màu](docs/product-models-versions-and-colors.md).

Bản hoàn thiện có hai nhóm giao diện: **Storefront** cho khách mua hàng và **Dashboard** cho quản trị viên, quản lý, nhân viên kho.

<!-- Danh mục tham chiếu mở rộng theo yêu cầu, không tự nhập sản phẩm vào các nhóm mới. -->
Ngày 02/10/2026, đã mở rộng menu theo ảnh CellphoneS: Điện thoại, Laptop, Âm thanh/Mic, Đồng hồ/Camera và Gia dụng/Làm đẹp. V9/V10 có cây tối đa ba cấp, hãng dùng chung và form ADMIN chọn nhóm/hãng khi thêm sản phẩm. Nhóm chưa có hàng hiển thị kết quả trống; xem [Danh mục và hãng](docs/catalog-categories-and-brands.md).

## 2. Nền tảng đã có

Các chức năng dưới đây đã có trong source và các đợt kiểm chứng trước:

- **Tài khoản:** đăng ký khách hàng, đăng nhập JWT/BCrypt, xem thông tin tài khoản; chặn tài khoản không hoạt động.
- **Danh mục và sản phẩm:** danh sách/chi tiết API, tìm tên hoặc SKU, lọc danh mục/trạng thái, phân trang; ADMIN tạo danh mục/sản phẩm và sửa thông tin được hỗ trợ.
- **Ảnh sản phẩm:** lưu URL ảnh bìa qua API và form quản trị; có xem trước, thay ảnh và xóa ảnh. Chưa có tải tệp lên hoặc thư viện nhiều ảnh.
- **Kho và tồn kho:** ba kho, phân công nhân viên, nhập hàng, tra cứu khả dụng/đang giữ/tồn thực tế, lịch sử biến động bất biến.
- **Đặt hàng:** giỏ nhiều sản phẩm ở frontend, khách chọn chi nhánh, nhập người nhận/điện thoại/địa chỉ/ghi chú, tạo đơn và giữ hàng 15 phút; miễn phí giao hàng, thanh toán mô phỏng, hủy và hết hạn. V15 chụp thông tin nhận hàng cố định trên từng đơn.
- **Xử lý đơn:** danh sách vận hành theo kho, đóng gói, giao hàng có mã vận đơn, hoàn tất giao, nhận trả toàn bộ hàng và hoàn tiền mô phỏng.
- **Báo cáo:** doanh thu theo ngày/tháng/kho, sản phẩm bán chạy, hàng sắp hết và tổng hợp trạng thái đơn.
- **Giao diện:** cửa hàng cho khách và dashboard theo vai trò, cùng chạy với Spring Boot.
- **Portfolio:** Swagger/OpenAPI, migration Flyway, Docker Compose, workflow GitHub Actions và tài liệu đo SQL bằng PostgreSQL.

Lần kiểm chứng được ghi nhận gần nhất có **219 test PASS**, `test`/`package` thành công và **72 kiểm tra Chrome PASS** trên PostgreSQL QA. Đây là kết quả lịch sử trong [docs/tech-store.md](docs/tech-store.md), không phải kết quả chạy mới mỗi khi sửa tài liệu. Suite tích hợp tự động hiện dùng H2; Testcontainers PostgreSQL chưa được triển khai.

Ngày 01/10/2026, catalog cũ trong database cục bộ đã được sao lưu và làm trống theo yêu cầu của chủ cửa hàng. Giữ năm danh mục công nghệ, tài khoản và ba kho để tự nhập sản phẩm. `DEMO_SEED_CATALOG` mặc định là `false`; startup không được tự xóa dữ liệu hoặc tự bơm lại hàng mẫu. Chi tiết reset và bản sao lưu nằm trong tài liệu công nghệ ở trên.

## 3. Quy tắc phải giữ khi phát triển

1. **Một đơn thuộc một kho.** Khách chọn kho/chi nhánh phục vụ khi đặt hàng; chưa tự định tuyến hoặc tách đơn qua nhiều kho.
2. **Backend quyết định giá và quyền.** Không tin tổng tiền, vai trò, chủ đơn hoặc trạng thái tồn kho do frontend gửi lên.
3. **Không bán vượt tồn.** Tiếp tục giữ atomic conditional update để reserve; thiếu một mặt hàng phải rollback toàn bộ đơn nhiều sản phẩm.
4. **Khóa theo thứ tự thống nhất.** Các nghiệp vụ nhiều mặt hàng xử lý theo inventory ID tăng dần để giảm nguy cơ deadlock.
5. **Mọi thay đổi tồn có movement.** Sổ cái giữ actor, tham chiếu và số tồn trước/sau; sửa sai bằng movement mới, không sửa hoặc xóa lịch sử nghiệp vụ.
6. **Giữ thời điểm DISPATCH.** Thanh toán thành công chuyển `PENDING → CONFIRMED`, trừ reserved và ghi DISPATCH. Đóng gói/giao/hoàn tất không trừ kho lần nữa.
7. **Hủy và trả hàng đúng trạng thái.** Hủy PENDING/hết hạn giải phóng hàng giữ; quản lý hủy CONFIRMED/PACKED trước SHIPPED thì hoàn kho/tiền. DELIVERED → RETURNED nhận lại toàn bộ hàng, ghi RETURN_RESTOCK và hoàn tiền mô phỏng trong cùng transaction.
8. **Quyền được kiểm tra tại backend.** CUSTOMER chỉ xem/thao tác đơn của mình; STAFF chỉ vận hành kho được phân công; ledger và báo cáo dành cho MANAGER/ADMIN; ghi catalog dành cho ADMIN.
9. **Thao tác lặp không ghi trùng.** Thanh toán, vận đơn và hoàn kho phải giữ kiểm tra trạng thái, khóa order và idempotency hiện có.
10. **Giữ migration đã áp dụng.** Chỉ thêm migration mới khi schema cần thay đổi; giữ cơ chế `ddl-auto=validate` và các phần đang hoạt động.

Giỏ hàng tiếp tục ở frontend cho MVP. Việc thêm bảng `carts` chỉ được xem xét khi cần lưu giỏ giữa nhiều phiên hoặc thiết bị.

## 4. Trải nghiệm cần hoàn thiện

### Khách hàng

Khách chọn chi nhánh, tìm và xem chi tiết sản phẩm, biết tình trạng hàng tại chi nhánh đó, thêm giỏ, điền thông tin nhận hàng, đặt đơn, thanh toán và theo dõi giao nhận. Sau khi nhận hàng, khách có thể đánh giá sản phẩm đã mua.

Khách có hồ sơ cá nhân để xem/chỉnh sửa những trường được cho phép. Giao diện cửa hàng chỉ hiển thị dữ liệu mua sắm, lịch sử và thông tin của chính khách đó.

### Quản trị viên và nhân viên

ADMIN quản lý nội dung sản phẩm, ảnh và trạng thái bán. ADMIN/STAFF nhập hàng đúng quyền; các vai trò vận hành xử lý đơn theo kho, theo dõi vận đơn và thực hiện nhận trả. MANAGER/ADMIN xem báo cáo và lịch sử kiểm toán.

API quản lý tài khoản, đổi vai trò và phân công nhân viên qua dashboard chưa có đầy đủ; đó là công việc phát triển tiếp theo, không coi tài khoản demo có sẵn là đã hoàn thành phân hệ này.

## 5. Lộ trình tiếp theo theo mức ưu tiên

Các chặng dưới đây nối tiếp những milestone đã hoàn thành. Mỗi chặng cần chia thành lượt nhỏ: thống nhất contract, làm API/migration/test, rồi nối giao diện.

### Ưu tiên 1 — Checkout có thông tin nhận hàng

<!-- Quyết định người dùng ngày 04/10/2026 được triển khai ở V15, các mục bên dưới là tiêu chí đã hoàn thành. -->
**Đã triển khai V15:** tên người nhận, số điện thoại và địa chỉ bắt buộc; ghi chú tùy chọn; miễn phí giao hàng. Bản chụp cố định trên đơn, muốn đổi phải hủy PENDING và đặt lại. Đơn cũ có `delivery=null` vẫn đọc/thanh toán/hủy/giao/hoàn được. Form giỏ gửi contract mới; khách và nhân viên đúng quyền xem thông tin tại chi tiết đơn. Xem [checkout và kiểm chứng](docs/checkout-delivery.md).

**Mục tiêu:** một đơn chứa đủ thông tin để cửa hàng giao hàng cho khách.

- Chốt các trường người nhận, số điện thoại, địa chỉ và ghi chú giao hàng.
- Chốt quy tắc phí giao hàng và thời điểm được sửa thông tin nhận hàng trước khi đổi schema.
- Thêm snapshot thông tin nhận hàng vào đơn bằng migration mới, tránh phụ thuộc hồ sơ có thể thay đổi sau đó.
- Bổ sung DTO/validation, API tạo/đọc đơn và form checkout; dữ liệu đơn cũ cần tiếp tục đọc được.
- Giữ nguyên chọn chi nhánh, snapshot giá, reserve nhiều mặt hàng và thời hạn 15 phút.

**Nghiệm thu:** đơn mới lưu/trả đúng thông tin nhận hàng; đổi hồ sơ không làm đổi đơn cũ; dữ liệu sai trả 400; khách khác không đọc được đơn; thiếu hàng vẫn rollback toàn bộ order/items/reservation/movements.

### Ưu tiên 2 — Catalog đủ thông tin để quyết định mua

**Mục tiêu:** khách hiểu sản phẩm và tình trạng còn hàng trước khi đặt.

- Mô tả tối đa 5.000 ký tự có trong V7; hãng/cây danh mục có trong V9/V10. V12 có một thẻ/model, tối đa 20 phiên bản, 30 màu/phiên bản và 100 SKU/model; thông số chung/riêng tối đa 60 dòng hiệu lực. ADMIN nhập dữ liệu thật. Lọc RAM/chip/màn hình chuyên biệt và merge SKU cũ vẫn là mở rộng riêng, không suy đoán từ tên/mô tả.
- Khách bấm thẻ để mở trang chi tiết `/san-pham/{id}`, chọn số lượng/chi nhánh rồi thêm giỏ. Trang có URL chia sẻ, hỗ trợ tab mới, tải lại và Back/Forward; giỏ vẫn giữ trong các lần điều hướng nội bộ.
- Menu nhiều cột, nhóm cha–con, hãng và giá đã có; lọc/sắp xếp tại database trước phân trang. ADMIN tạo nhóm/hãng; V9/V10 chuẩn bị tham chiếu mà không sinh sản phẩm/tồn mẫu. Thông số và nhóm phiên bản/màu V12 đã có; xem [Danh mục và hãng](docs/catalog-categories-and-brands.md), [Model, phiên bản và màu](docs/product-models-versions-and-colors.md).
- Bổ sung dữ liệu public về khả dụng/tình trạng hàng theo chi nhánh. Không mở API tồn kho nội bộ cho CUSTOMER hoặc lộ reserved/ledger.
- Giữ kiểm tra reserve ở backend: số hiển thị trên trang có thể thay đổi khi nhiều người cùng mua.
- Thống nhất cách xử lý sản phẩm INACTIVE ở danh sách, trang chi tiết và đơn lịch sử.
- Bỏ các giá giảm, sao đánh giá và số lượt bán giả lập khỏi bản bán hàng hoàn thiện; dùng dữ liệu thật hoặc hiển thị rõ trạng thái chưa có dữ liệu.

**Nghiệm thu:** khách xem đúng mô tả/ảnh/giá và tình trạng theo chi nhánh; bộ lọc hoạt động trước phân trang; ADMIN sửa được nội dung; sản phẩm có trong đơn cũ vẫn đọc được; hai khách tranh mua không làm tồn kho âm.

Ảnh bìa và thư viện tối đa 8 ảnh bổ sung đã quản lý được bằng URL qua ADMIN trong V8. Khách bấm thumbnail hoặc nút trước/sau ở trang chi tiết; xem [hướng dẫn bộ ảnh](docs/product-gallery.md). Tải tệp lên, lưu trữ và tối ưu dung lượng ảnh là phần mở rộng riêng.

### Ưu tiên 3 — Hồ sơ khách hàng và đánh giá sau mua

Triển khai thành hai lượt độc lập:

1. **Hồ sơ:** thêm API sửa những trường được cho phép, validation và giao diện hồ sơ. Quy tắc đổi email/mật khẩu cần được chốt riêng; khách không được tự sửa role hoặc status.
2. **Đánh giá:** khách chỉ đánh giá sản phẩm có trong đơn đủ điều kiện đã giao. Chốt số lần đánh giá, sửa/xóa và xử lý đánh giá khi đơn được trả lại trước khi thêm schema.

Tính điểm trung bình và số đánh giá từ dữ liệu thật. Nhãn/số lượng bán chạy phải có nguồn thống kê và khoảng thời gian rõ ràng; không dùng số cố định để mô tả doanh số thực.

**Nghiệm thu:** khách chỉ sửa hồ sơ của mình; người chưa mua hoặc khách của đơn khác không được đánh giá; cơ chế chống đánh giá trùng hoạt động; tổng hợp điểm/số lượng phản ánh đúng dữ liệu.

### Ưu tiên 4 — Làm rõ yêu cầu thanh toán của bài tập lớn

Hiện hệ thống chỉ có **thanh toán mô phỏng**. Nếu tiêu chí môn học yêu cầu tích hợp cổng thanh toán, cần thống nhất với giảng viên việc sử dụng **sandbox**; nút xác nhận mô phỏng hiện tại chưa đáp ứng phần tích hợp đó.

- Chọn một cổng có môi trường thử nghiệm theo yêu cầu nghiệm thu.
- Chốt ánh xạ kết quả thanh toán vào trạng thái order/payment, đặc biệt khi callback đến sau hết hạn hoặc hủy đơn.
- Backend xác minh kết quả và chữ ký callback theo contract đã chọn; không xác nhận PAID chỉ vì frontend chuyển về trang thành công.
- Xử lý callback lặp và tranh chấp với cancel/expiry bằng cơ chế transaction/khóa/idempotency.
- Tách cấu hình sandbox khỏi môi trường demo; không dùng tiền thật trong MVP.

**Nghiệm thu:** kết quả hợp lệ chỉ dispatch một lần; callback sai bị từ chối; thanh toán thất bại/lặp/đến muộn có hành vi đã thống nhất và test tương ứng.

Vận chuyển tiếp tục mô phỏng bằng shipment/tracking; chưa cần tích hợp đơn vị giao hàng thật.

### Ưu tiên 5 — Củng cố kiểm thử và vận hành

Ưu tiên **Testcontainers PostgreSQL** để chạy migration, trigger bất biến và concurrency trên database đích. Chọn chiến lược chuyển suite hoặc bổ sung suite PostgreSQL riêng; giữ các test hồi quy có giá trị và cấu hình CI phù hợp.

Sau đó triển khai từng phần có mục tiêu rõ ràng:

- Correlation ID và log có ngữ cảnh để theo dõi một request qua các lớp.
- Actuator/metrics; chỉ công khai health phù hợp, giới hạn các thông tin vận hành khác.
- Refresh token và thu hồi phiên đăng nhập, có quy tắc logout và test quyền tương ứng.
- Khóa scheduler phân tán khi triển khai nhiều instance; bảo đảm expiry không giải phóng kho trùng.
- Cache catalog khi có số liệu hoặc nhu cầu đọc nhiều, kèm quy tắc xóa/cập nhật cache sau thay đổi dữ liệu.

**Nghiệm thu:** suite PostgreSQL chạy được trong môi trường đã chỉ định; UPDATE/DELETE ledger thật bị chặn; concurrency/rollback/ownership/phạm vi kho được kiểm chứng; log không chứa mật khẩu hoặc token; metrics và phiên đăng nhập có quyền rõ ràng.

## 6. Các mở rộng để sau luồng bán hàng chính

- **Quản lý người dùng và phân công kho:** ADMIN quản lý trạng thái/quyền/phân công với kiểm tra quyền và truy vết thay đổi.
- **Chuyển kho:** luồng xuất chuyển, hàng đang vận chuyển và nhận tại kho đích; phải chốt trạng thái, thời điểm giảm/tăng và movement tương ứng.
- **Kiểm kê/điều chỉnh:** ghi nhận chênh lệch, lý do, người duyệt và STOCK_ADJUSTMENT; không sửa trực tiếp ledger.
- **Khuyến mãi:** voucher hoặc giá ưu đãi thật, với quy tắc tính giá và snapshot đơn.
- **Trải nghiệm phụ:** wishlist, email cập nhật đơn, trang chính sách/FAQ, gợi ý sản phẩm.
- **Phạm vi nâng cao:** tự phân bổ kho, tách đơn, trả một phần, bảo hành và serial chỉ làm khi được chọn thành yêu cầu riêng.

Giữ kiến trúc modular monolith hiện tại. Việc thêm Redis, hàng đợi hoặc tách dịch vụ cần giải quyết một vấn đề đã xác định và có cách kiểm chứng.

## 7. Tiêu chuẩn triển khai từng lượt

- Đọc source và handoff trước khi sửa; ghi rõ phần giữ nguyên, phần thay đổi và contract bị ảnh hưởng.
- Hỏi chủ dự án trước phần phụ thuộc nếu có nhiều cách hiểu ảnh hưởng lớn đến schema hoặc vòng đời đơn.
- Ưu tiên backend/API, migration và test nghiệp vụ trước khi nối frontend.
- Dùng DTO, Bean Validation, truy vấn có tham số và phân trang tại database; kiểm tra quyền cả HTTP và service khi cần.
- File mới/sửa có comment hoặc JavaDoc tiếng Việt có dấu, UTF-8; SQL trình bày nhiều dòng, dùng Java Text Block khi viết trong Java.
- Dữ liệu thử đặt trong môi trường QA riêng; không xóa catalog người dùng tự nhập để chạy kiểm thử.
- Chạy các test phù hợp; khi thay backend, hoàn tất test/build theo phạm vi và báo cáo giới hạn kiểm chứng.

Lệnh Maven trên máy Windows hiện tại:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

## 8. Mốc hoàn thiện để trình diễn và đưa vào CV

- [x] Backend đặt hàng/tồn kho có transaction và chống bán vượt tồn.
- [x] Ledger bất biến, phân quyền theo role/chủ đơn/kho.
- [x] Fulfillment, vận đơn, báo cáo và storefront/dashboard.
- [x] Ảnh bìa quản lý qua ADMIN; catalog nhập tay.
- [x] Bộ ảnh có thứ tự, thumbnail và nút chuyển ảnh; thêm/sửa/xóa link qua form ADMIN.
- [x] Menu danh mục, lọc khoảng giá và sắp xếp/phân trang với ID làm thứ tự phụ.
- [x] Hãng dùng chung, danh mục tối đa ba cấp và form ADMIN; 353 test/84 kiểm tra Chrome PASS.
- [x] Checkout có thông tin nhận hàng đã được chốt và lưu snapshot bằng V15; miễn phí giao hàng.
- [x] Mô tả văn bản, trang chi tiết có URL riêng và form ADMIN chỉnh sửa nội dung.
- [ ] Tình trạng hàng public theo chi nhánh và thuộc tính cấu trúc để lọc.
- [ ] Hồ sơ khách, đánh giá sau mua và số liệu hiển thị thật.
- [ ] Đáp ứng yêu cầu thanh toán của môn học bằng hình thức được nghiệm thu.
- [ ] Kiểm chứng tự động migration/trigger/concurrency trên PostgreSQL.
- [ ] Chạy lại Docker Compose và xác nhận CI trên GitHub cho phiên bản hoàn thiện.
- [ ] Chuẩn bị catalog phù hợp, video/demo và hướng dẫn cho người đánh giá.

Kịch bản demo nên đi trọn luồng: ADMIN thêm sản phẩm/ảnh → nhập kho → CUSTOMER xem hàng và đặt đơn → thanh toán → STAFF đóng gói/giao → hoàn tất → xem báo cáo → nhận trả và đối chiếu movement. Thêm một tình huống tranh mua và một tình huống truy cập sai kho để chứng minh tính đúng đắn của backend.

Tài liệu liên quan: [README](README.md), [bàn giao dự án](ANTIGRAVITY_HANDOFF.md), [lộ trình API hiện có](docs/storefront-roadmap.md), [phạm vi StockFlow Tech và kiểm chứng](docs/tech-store.md), [hướng dẫn ảnh](docs/product-images.md), [đo tối ưu SQL](docs/sql-optimization-report.md).
