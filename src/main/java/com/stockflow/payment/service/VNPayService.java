package com.stockflow.payment.service;

import com.stockflow.common.config.VNPayConfig;
import com.stockflow.common.exception.*;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.order.service.OrderService;
import com.stockflow.payment.domain.PaymentTransaction;
import com.stockflow.payment.dto.PaymentResult;
import com.stockflow.payment.repository.PaymentTransactionRepository;
import com.stockflow.payment.util.VNPayUtil;
import com.stockflow.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VNPayService {
    private static final Logger log = LoggerFactory.getLogger(VNPayService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private final VNPayConfig.Properties config;
    private final String tmnCode;
    private final String hashSecret;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentTransactionRepository transactions;
    private final AtomicLong referenceClock = new AtomicLong();

    public VNPayService(VNPayConfig.Properties config, OrderRepository orders, OrderService orderService,
                        PaymentTransactionRepository transactions) {
        this.config = config;
        this.tmnCode = config.tmnCode().trim();
        this.hashSecret = config.hashSecret().trim();
        this.orders = orders;
        this.orderService = orderService;
        this.transactions = transactions;
    }

    @jakarta.annotation.PostConstruct
    public void logRuntimeTmnCode() {
        log.info("[VNPAY] TMN Code đang chạy: {}", tmnCode);
        log.info("[VNPAY] Secret key kiểm tra: {}", VNPayUtil.maskedSecret(hashSecret));
        if (hashSecret.length() != 32 || !hashSecret.startsWith("XWMV") || !hashSecret.endsWith("TFFC")) {
            log.warn("[VNPAY] Secret key không khớp dấu hiệu Sandbox mong đợi XWMV...TFFC / độ dài 32. Kiểm tra biến môi trường của ứng dụng.");
        }
    }

    @Transactional
    public String createPaymentUrl(Long orderId, HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User customer)) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thanh toán.");
        }
        Order order = lockedOrder(orderId);
        if (!customer.getRole().getName().equals("CUSTOMER") || !order.getCustomerId().equals(customer.getId())) {
            throw new ForbiddenException("Bạn chỉ được thanh toán đơn của chính mình.");
        }
        Instant now = Instant.now();
        if (order.getStatus() != OrderStatus.PENDING || !order.getReservationExpiresAt().isAfter(now)) {
            throw new ConflictException("Đơn không còn trong thời hạn chờ thanh toán.");
        }
        if (order.getTotalAmount().signum() <= 0) {
            throw new BadRequestException("VNPay yêu cầu số tiền thanh toán lớn hơn 0.");
        }
        long stamp = referenceClock.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
        String reference = orderId + "_" + stamp;
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Version", config.version());
        parameters.put("vnp_Command", config.command());
        parameters.put("vnp_TmnCode", tmnCode);
        parameters.put("vnp_Amount", order.getTotalAmount().movePointRight(2).toBigIntegerExact().toString());
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_TxnRef", reference);
        parameters.put("vnp_OrderInfo", "ThanhToanDonHang" + orderId);
        parameters.put("vnp_OrderType", "other");
        parameters.put("vnp_Locale", "vn");
        parameters.put("vnp_ReturnUrl", config.returnUrl());
        // Không tin X-Forwarded-For do client tự gửi; proxy tin cậy có thể cấu hình tại servlet container.
        String remoteAddress = request.getRemoteAddr();
        parameters.put("vnp_IpAddr", "0:0:0:0:0:0:0:1".equals(remoteAddress) || "::1".equals(remoteAddress)
                ? "127.0.0.1" : remoteAddress);
        parameters.put("vnp_CreateDate", DATE.format(now.atZone(VN_ZONE)));
        parameters.put("vnp_ExpireDate", DATE.format(order.getReservationExpiresAt().atZone(VN_ZONE)));
        var encoded = VNPayUtil.buildParameters(parameters);
        String hashData = encoded.hashData();
        String signature = VNPayUtil.hmacSha512(hashSecret, hashData);
        String paymentUrl = config.payUrl() + "?" + encoded.query() + "&vnp_SecureHash=" + signature;
        logRuntimeTmnCode();
        log.info("[VNPAY] Hash Data: {}", hashData);
        log.info("[VNPAY] Secure Hash: {}", signature);
        log.info("[VNPAY] paymentUrl: {}", paymentUrl);
        if ("DEMOVNPAY".equals(tmnCode) || "DEMOHASHSECRETKEY2026".equals(hashSecret)) {
            log.warn("[VNPAY] Đang dùng credential placeholder. Cần VNPAY_TMN_CODE và VNPAY_HASH_SECRET của cùng merchant Sandbox để VNPay xác minh chữ ký.");
        }
        transactions.save(new PaymentTransaction(orderId, reference, order.getTotalAmount(), now));
        return paymentUrl;
    }

    @Transactional
    public PaymentResult handleReturn(Map<String, String> queryParams) {
        if (!VNPayUtil.validSignature(hashSecret, queryParams)) {
            throw new BadRequestException("Chữ ký VNPay không hợp lệ.");
        }
        if (!tmnCode.equals(queryParams.get("vnp_TmnCode"))) {
            throw new BadRequestException("Mã merchant VNPay không hợp lệ.");
        }
        String reference = required(queryParams, "vnp_TxnRef", "[0-9]+_[0-9]+");
        Long orderId = transactions.findOrderIdByTxnRef(reference)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy giao dịch VNPay."));
        // Khóa order trước khi đọc trạng thái transaction: chung thứ tự với mô phỏng/cancel/expiry.
        Order order = lockedOrder(orderId);
        PaymentTransaction transaction = transactions.findByTxnRef(reference).orElseThrow();
        String amount = required(queryParams, "vnp_Amount", "[0-9]{1,12}");
        if (!transaction.getAmount().movePointRight(2).toBigIntegerExact().toString().equals(amount)
                || transaction.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BadRequestException("Số tiền VNPay không khớp với đơn hàng.");
        }
        String responseCode = required(queryParams, "vnp_ResponseCode", "[0-9]{2}");
        String transactionStatus = required(queryParams, "vnp_TransactionStatus", "[0-9]{2}");
        String transactionCode = required(queryParams, "vnp_TransactionNo", "[0-9]{1,100}");
        String bankCode = queryParams.get("vnp_BankCode");
        if (bankCode != null && !bankCode.matches("[A-Za-z0-9]{1,20}")) {
            throw new BadRequestException("Mã ngân hàng VNPay không hợp lệ.");
        }
        Instant payDate = parsePayDate(queryParams.get("vnp_PayDate"));
        boolean paid = responseCode.equals("00") && transactionStatus.equals("00");
        if (paid && payDate == null) throw new BadRequestException("Thiếu ngày thanh toán VNPay.");

        // Callback lặp không thay đổi giao dịch đã hoàn tất hay ghi thêm ledger.
        if (transaction.getStatus() != PaymentTransaction.Status.PENDING) {
            return new PaymentResult(orderId, transaction.getStatus() == PaymentTransaction.Status.SUCCESS);
        }
        boolean success = false;
        if (paid && order.getStatus() == OrderStatus.PENDING) {
            success = orderService.confirmVerifiedVNPayPayment(orderId).status() == OrderStatus.CONFIRMED;
        }
        transaction.complete(success, responseCode, transactionCode, bankCode, payDate);
        return new PaymentResult(orderId, success);
    }

    private Order lockedOrder(Long id) {
        return orders.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
    }

    private String required(Map<String, String> parameters, String key, String pattern) {
        String value = parameters.get(key);
        if (value == null || !value.matches(pattern)) throw new BadRequestException("Tham số VNPay không hợp lệ: " + key);
        return value;
    }

    private Instant parsePayDate(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value, DATE).atZone(VN_ZONE).toInstant();
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("Ngày thanh toán VNPay không hợp lệ.");
        }
    }
}
