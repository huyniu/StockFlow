package com.stockflow.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@stockflow.com}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async("mailTaskExecutor")
    public void sendVerificationOtp(String toEmail, String otpCode) {
        log.info("[EMAIL_OTP] >>> Email: {} | Mã OTP: {} (hết hạn sau 15 phút) <<<", toEmail, otpCode);
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP chưa được cấu hình; mã OTP được hiển thị trong console.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("StockFlow - Xác thực email");
        message.setText("Mã OTP của bạn: " + otpCode + "\nMã có hiệu lực trong 15 phút.");
        try {
            sender.send(message);
        } catch (org.springframework.mail.MailException exception) {
            log.error("Không gửi được email OTP tới {}. Vui lòng kiểm tra SMTP.", toEmail);
        }
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
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) { log.warn("SMTP chưa được cấu hình; không thể gửi email đặt lại mật khẩu."); return; }
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(toEmail); message.setSubject(subject); message.setText(text);
        try { sender.send(message); }
        catch (org.springframework.mail.MailException e) { log.error("Không gửi được email đặt lại mật khẩu; kiểm tra cấu hình SMTP."); }
    }
}
