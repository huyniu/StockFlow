package com.stockflow.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.repository.OrderCreationRequestRepository;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bổ sung idempotency cho HTTP đặt hàng; giữ nguyên transaction và nghiệp vụ tồn của OrderService. */
@Service
public class OrderPlacementService {

    private final OrderService orders;
    private final OrderCreationRequestRepository requests;
    private final ObjectMapper json;
    private final Validator validator;

    /** Nhận dịch vụ đặt hàng hiện có, kho khóa và bộ kiểm tra/chuẩn hóa payload. */
    public OrderPlacementService(
            OrderService orders,
            OrderCreationRequestRepository requests,
            ObjectMapper json,
            Validator validator) {
        this.orders = orders;
        this.requests = requests;
        this.json = json;
        this.validator = validator;
    }

    /** Cùng khách/khóa/nội dung trả cùng đơn; đổi nội dung dưới khóa cũ trả 409, không reserve lần nữa. */
    @Transactional
    public OrderResponse place(CreateOrderRequest request, Long customerId, String key) {
        if (key == null) {
            // Client cũ và bộ test hiện có tiếp tục dùng contract không bắt buộc header mới.
            return orders.createOrder(request, customerId);
        }
        if (!key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new BadRequestException("Idempotency-Key gồm 1–128 ký tự chữ, số hoặc . _ : -.");
        }
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BadRequestException("Dữ liệu đặt hàng không hợp lệ.");
        }
        String hash = fingerprint(request);
        boolean first = requests.claim(customerId, key, hash);
        var saved = requests.findLocked(customerId, key)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy khóa đặt hàng vừa ghi nhận."));
        if (!saved.hash().equals(hash)) {
            throw new ConflictException("Khóa đặt hàng đã dùng cho nội dung khác. Vui lòng tạo lần đặt hàng mới.");
        }
        if (saved.orderId() != null) {
            // Đọc lại trạng thái hiện tại, vẫn qua kiểm tra chủ đơn/tài khoản ở OrderService.
            return orders.getOrder(saved.orderId(), customerId);
        }
        if (!first) {
            throw new ConflictException("Lần đặt hàng này đang được xử lý. Vui lòng thử lại.");
        }
        OrderResponse order = orders.createOrder(request, customerId);
        requests.complete(saved.id(), order.id());
        return order;
    }

    /** Hash nội dung DTO đã chuẩn hóa; thứ tự mặt hàng không biến cùng một giỏ thành yêu cầu khác. */
    private String fingerprint(CreateOrderRequest request) {
        var canonical = new CreateOrderRequest(
                request.warehouseId(),
                request.items().stream().sorted(Comparator.comparing(CreateOrderRequest.Item::productId)).toList(),
                request.delivery(), request.toDistrictId(), request.toWardCode(), request.shippingFee());
        try {
            byte[] bytes = json.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Không thể tạo dấu vân tay của yêu cầu đặt hàng.", exception);
        }
    }
}
