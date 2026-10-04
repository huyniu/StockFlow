package com.stockflow.catalog.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Mở trang chi tiết sản phẩm bằng đường dẫn công khai để khách chia sẻ, mở tab mới hoặc tải lại.
 * Giao diện dùng API catalog hiện có; controller này không đọc kho hoặc thay đổi dữ liệu nghiệp vụ.
 */
@Controller
public class ProductPageController {

    /**
     * Chuyển nội bộ tới giao diện tĩnh và giữ URL sản phẩm trên thanh địa chỉ.
     * JavaScript đọc ID từ URL, tải thông tin mới nhất và hiển thị lỗi nếu sản phẩm không tồn tại.
     */
    @GetMapping("/san-pham/{id:[0-9]+}")
    public String productDetailPage() {
        return "forward:/index.html";
    }
}
