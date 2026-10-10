package com.stockflow.auth.service;

import com.stockflow.common.mail.MailDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final MailDeliveryService delivery;

    public EmailService(MailDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Async("mailTaskExecutor")
    public void sendVerificationOtp(String toEmail, String otpCode) {
        send(toEmail, "StockFlow - Xác thực email", "Mã OTP của bạn: " + otpCode
                + "\nMã có hiệu lực trong 15 phút.");
    }

    @Async("mailTaskExecutor")
    public void sendPasswordResetOtp(String toEmail, String otpCode) {
        send(toEmail, "StockFlow - Đặt lại mật khẩu", "Mã đặt lại mật khẩu: " + otpCode
                + "\nMã có hiệu lực trong 15 phút, chỉ được dùng một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
    }

    @Async("mailTaskExecutor")
    public void sendPasswordChanged(String toEmail) {
        send(toEmail, "StockFlow - Mật khẩu đã được thay đổi", "Mật khẩu StockFlow của bạn vừa được thay đổi. Các phiên đăng nhập cũ đã được kết thúc. Nếu không phải bạn, hãy liên hệ cửa hàng ngay.");
    }

    private void send(String toEmail, String subject, String text) {
        if (!delivery.isConfigured()) {
            log.warn("[MAIL] Chưa cấu hình dịch vụ gửi email; kiểm tra biến môi trường mail.");
            return;
        }
        try {
            delivery.send(toEmail, subject, text);
        } catch (MailException exception) {
            log.error("[MAIL] Không gửi được email; kiểm tra cấu hình dịch vụ và kết nối.");
        }
    }
}
