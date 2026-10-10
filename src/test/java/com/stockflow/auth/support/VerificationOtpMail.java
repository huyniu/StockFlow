package com.stockflow.auth.support;

import com.stockflow.auth.service.EmailService;
import org.mockito.Mockito;

/** Đọc OTP từ mail mock, không cần lưu mã rõ hoặc đưa mã vào output test. */
public final class VerificationOtpMail {
    private VerificationOtpMail() {}
    public static String latest(EmailService mail, String email) {
        return Mockito.mockingDetails(mail).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("sendVerificationOtp")
                        && email.equals(call.getArgument(0)))
                .reduce((previous, current) -> current)
                .map(call -> (String) call.getArgument(1)).orElseThrow();
    }
}
