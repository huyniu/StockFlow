<!-- Hướng dẫn logo thương hiệu ngày 03/10/2026; ảnh lưu trong ứng dụng, đường dẫn được quản lý qua API ADMIN. -->
# Logo thương hiệu trong StockFlow

Đã gán logo cho **84/84 thương hiệu hiện có** qua API quản trị trên ứng dụng cục bộ: giữ nguyên Apple, Samsung, Sony và HP; bổ sung 80 hãng còn thiếu. Bộ ảnh gồm 32 SVG và 52 PNG trong `src/main/resources/static/assets/brands/`, được đóng gói cùng JAR và hiển thị bằng đường dẫn `/assets/brands/...`. Không cần sửa `app.js` khi thay logo.

## Nguồn ảnh và đường dẫn

Nguồn gồm [Simple Icons CDN](https://github.com/LitoMore/simple-icons-cdn), ảnh thương hiệu trong các danh mục CellphoneS và website chính thức của hãng. Bộ vector [Simple Icons](https://github.com/simple-icons/simple-icons) công bố theo CC0-1.0; SVG có comment nguồn. PNG giữ ảnh tải về, nguồn được ghi trong [danh sách đủ 84 logo](brand-logo-sources.md).

<!-- Bốn logo đầu tiên được giữ nguyên khi bổ sung các hãng còn thiếu. -->

- Apple: [ảnh nguồn](https://cdn.simpleicons.org/apple/111827), đường dẫn trong StockFlow: `/assets/brands/apple.svg`.
- Samsung: [ảnh nguồn](https://cdn.simpleicons.org/samsung/1428A0?viewbox=auto), đường dẫn trong StockFlow: `/assets/brands/samsung.svg`.
- Sony: [ảnh nguồn](https://cdn.simpleicons.org/sony/111827?viewbox=auto), đường dẫn trong StockFlow: `/assets/brands/sony.svg`.
- HP: [ảnh nguồn](https://cdn.simpleicons.org/hp/0096D6), đường dẫn trong StockFlow: `/assets/brands/hp.svg`.

Samsung và Sony dùng `viewbox=auto` để logo dạng chữ vừa khung, không bị thu nhỏ do khoảng trống trong ảnh nguồn. Trình duyệt tải bản ảnh trong ứng dụng cho cả 84 hãng, không phụ thuộc CDN khi xem menu hoặc quản trị. Riêng KAVVO có ảnh gốc vuông chứa khoảng trống; CSS căn giữa bằng `object-fit: cover` trong khung logo ngang để chữ dễ đọc. imoo dùng ảnh chữ màu cam đã đối chiếu từ trang giới thiệu hãng.

## Tự thêm hoặc thay logo

1. Đăng nhập ADMIN, vào **Danh mục và sản phẩm → Danh mục → Thương hiệu**.
2. Tìm hãng và bấm **Sửa logo**. Với hãng mới, bấm **Thêm thương hiệu**.
3. Dán đường dẫn ảnh trực tiếp vào ô logo, kiểm tra xem trước rồi bấm **Lưu logo**.
4. Để bỏ logo, bấm **Xóa logo** rồi lưu. Để đổi logo, thay đường dẫn rồi lưu.

Muốn tìm thêm ảnh: mở [Simple Icons](https://simpleicons.org), tìm tên hãng, tải SVG hoặc lấy slug của biểu tượng và dùng URL theo hướng dẫn CDN. Ví dụ cấu trúc URL: `https://cdn.simpleicons.org/<slug>`. Một số hãng không có trong thư viện; khi đó có thể dùng ảnh nhận diện từ website của hãng.

Nếu logo đã tải về máy, hiện chưa có nút upload. Đặt file trong `src/main/resources/static/assets/brands/`, khởi động lại ứng dụng hoặc build để cập nhật tài nguyên, rồi nhập `/assets/brands/ten-file.svg` trong form. Giữ logo dạng PNG/SVG rõ nét, ưu tiên nền trong suốt.

## Phạm vi thay đổi

- Bổ sung 80 ảnh vào thư mục tài nguyên thương hiệu, giữ bốn SVG đã có.
- Sửa `styles.css` để logo KAVVO vừa khung ngang; thêm `docs/brand-logo-sources.md` và cập nhật hai tài liệu quản lý logo.
- Cập nhật `logo_url` của 80 hãng còn thiếu qua `PATCH /api/v1/brands/{id}/logo`, có kiểm tra quyền ADMIN; giữ mọi URL đã được nhập trước lượt bổ sung.
- Không thêm migration. Đây là nội dung do chủ cửa hàng quản lý; database mới chưa được gán logo có thể dùng các đường dẫn trong danh sách nguồn.
- Bản đối chiếu trước/sau nằm trong `target/all-brand-logos/brands-before-apply.json` và `brands-after.json`, không lưu JWT. ID, tên, slug và liên kết danh mục được đối chiếu giữ nguyên.

Quyền API, xem trước ảnh và xử lý ảnh lỗi tiếp tục theo [hướng dẫn quản lý logo](catalog-completion-and-brand-logos.md).

## Kiểm chứng của lượt bổ sung ảnh

<!-- Kết quả test tập trung và trình duyệt, không coi là một lần chạy lại toàn bộ suite. -->
- `BrandLogoIntegrationTest` và `WebDemoIntegrationTest`: **37 test PASS**, 0 failure/error/skipped; `package` BUILD SUCCESS. Lệnh dùng `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`.
- Chrome: **103 kiểm chứng PASS**; đủ 84 hãng xuất hiện trong các menu cửa hàng và tải được ảnh trong quản trị. Form sửa/xem trước hoạt động; trang quản trị không tràn ngang ở độ rộng 390px; không có exception JavaScript.
- Cả 84 ảnh truy cập công khai HTTP 200 và khớp byte với source. 84 ảnh cùng CSS được kiểm tra hash trong JAR. Log và ảnh QA nằm trong `target/all-brand-logos/`.
- Không chạy lại toàn bộ suite hoặc kiểm thử vận hành đơn hàng ở lượt này vì không thay code nghiệp vụ/schema. Các test tập trung chạy trong lifecycle `package` với `-Dtest=BrandLogoIntegrationTest,WebDemoIntegrationTest`.
