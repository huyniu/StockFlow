package com.stockflow.auth.api;

import com.stockflow.auth.dto.ForgotPasswordRequest;
import com.stockflow.auth.dto.ResetPasswordRequest;
import com.stockflow.auth.service.PasswordResetService;
import com.stockflow.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {
    private final PasswordResetService service;
    // Bound abuse before expensive BCrypt work. Do not trust client-supplied forwarded headers.
    private final Map<String, Window> requests = new java.util.LinkedHashMap<>();
    private record Window(long startsAt, int count) {}
    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/forgot-password")
    public Map<String,String> request(@Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {
        limit(request.getRemoteAddr());
        service.requestReset(body.email());
        return Map.of("message", PasswordResetService.SENT_MESSAGE);
    }
    @PostMapping("/reset-password")
    public Map<String,String> reset(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        limit(request.getRemoteAddr());
        service.reset(body.email(), body.otp(), body.newPassword());
        return Map.of("message", "Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.");
    }
    private synchronized void limit(String ip) {
        long now = System.currentTimeMillis();
        requests.entrySet().removeIf(entry -> now - entry.getValue().startsAt() >= 60_000);
        Window old = requests.get(ip);
        if (old != null && old.count() >= 30) throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh. Vui lòng thử lại sau một phút.");
        if (old == null && requests.size() >= 2000) throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng thử lại sau một phút.");
        requests.put(ip, new Window(old == null ? now : old.startsAt(), old == null ? 1 : old.count()+1));
    }
}
