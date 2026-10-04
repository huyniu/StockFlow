package com.stockflow.order.support;

import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.DeliveryDetailsRequest;
import java.util.List;
import java.util.Map;

/** Fixture người nhận hợp lệ để các test tồn kho/catalog tiếp tục kiểm chứng đúng nghiệp vụ riêng. */
public final class CheckoutTestData {

    /** Không khởi tạo lớp tiện ích chứa dữ liệu kiểm thử. */
    private CheckoutTestData() {}

    /** Tạo yêu cầu có người nhận rõ ràng, không thêm constructor production tự bịa địa chỉ. */
    public static CreateOrderRequest orderRequest(Long warehouseId, List<CreateOrderRequest.Item> items) {
        return new CreateOrderRequest(warehouseId, items,
                new DeliveryDetailsRequest("Khách kiểm thử", "0901234567", "12 Phố Kiểm Thử, Hà Nội", null));
    }

    /** Dữ liệu JSON tương ứng contract checkout, dùng cho test HTTP không gọi DTO trực tiếp. */
    public static Map<String, Object> deliveryPayload() {
        return Map.of(
                "recipient_name", "Khách kiểm thử",
                "recipient_phone", "0901234567",
                "address", "12 Phố Kiểm Thử, Hà Nội");
    }
}
