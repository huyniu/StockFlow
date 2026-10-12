# Chatbot StockFlow

Chatbot mở từ **Liên hệ → Chat với trợ lý AI**. Các câu hỏi như “giá ip17”, “giá iPhone17” hoặc “muốn mua ip17” tra trực tiếp catalog công khai của StockFlow. Giá lấy từ sản phẩm ACTIVE, gộp phiên bản/màu và dùng giá từ giống trang sản phẩm; không suy đoán hàng còn trong kho. Sản phẩm không có trong catalog được thông báo rõ.

Các câu hỏi tư vấn khác dùng Gemini nếu cấu hình hợp lệ. Khi dịch vụ chưa kết nối được, chatbot thông báo tình trạng này và vẫn cho phép tra giá hoặc chuyển sang Zalo/điện thoại. Không tự đưa ra thời hạn bảo hành, chương trình giảm giá hay cam kết giao hàng chưa có trong dữ liệu.

## Localhost

- Biến môi trường `GEMINI_API_KEY` chứa khóa riêng của chủ hệ thống.
- Với profile `demo`, có thể dùng `.idea/gemini-local.properties`, nội dung `gemini.api-key=<khóa riêng>`. File này bị Git và Docker bỏ qua; không đưa vào commit, ảnh chụp hoặc log. Biến môi trường có ưu tiên cao hơn file này.
- Khóa từng nằm trong cấu hình nguồn đã được chuyển vào file riêng trên máy hiện tại. Các file YAML chỉ còn placeholder môi trường.
- `GEMINI_MODEL` mặc định là `gemini-3.5-flash-lite`. Chủ hệ thống có thể đặt model khác khả dụng cho khóa của mình. Model cũ `gemini-1.5-flash` trả HTTP 404 trong log localhost.
- Sau khi thay đổi Java hoặc cấu hình, khởi động lại Spring Boot. Tải lại trang để xóa các câu trả lời cũ trong phiên chat.

Model mặc định tham chiếu [danh sách model chính thức của Google](https://ai.google.dev/gemini-api/docs/models). Tên model trong cấu hình không chứng minh rằng khóa, quyền truy cập hoặc quota đã được xác minh.

Kiểm chứng localhost ngày **13/10/2026**, sau khi chủ hệ thống cung cấp khóa và yêu cầu xác minh: Google trả HTTP 200 cho danh sách model và có `gemini-3.5-flash-lite`. Gửi câu hỏi tư vấn laptop học Java qua `POST /api/v1/ai/chat` nhận HTTP 200, `aiGenerated=true` và câu trả lời tiếng Việt thực từ Gemini trong khoảng 1,9 giây. Kết quả này xác nhận kết nối tại thời điểm kiểm tra; không xác nhận Render hoặc đảm bảo quota về sau. Không lưu khóa trong bản ghi kiểm chứng.

## Render

Đặt `GEMINI_API_KEY` trong Environment của dịch vụ Render và chọn `GEMINI_MODEL` phù hợp. File `.idea/gemini-local.properties` trên máy không được gửi lên Render. Việc áp dụng mã mới lên Render cần một lần deploy được chủ hệ thống cho phép; chưa deploy hoặc xác minh Render trong lượt sửa này.

Catalog và giá phụ thuộc database của từng môi trường. Không sao chép dữ liệu local sang Aiven để làm chatbot trả lời giống nhau.

## Kiểm tra

```powershell
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" "-Dtest=AiChatEncodingTest,ChatCatalogServiceTest,GeminiChatServiceTest" test
node scripts/verify-storefront-ui.cjs --contact-ai-only
```

Kiểm thử Java dùng catalog và Gemini giả lập, không gửi khóa thật ra ngoài hoặc ghi database. Khóa Gemini được truyền bằng header `x-goog-api-key`; lỗi provider chỉ ghi mã HTTP, không ghi URL có khóa, nội dung request hay thông báo provider có thể chứa credential.
