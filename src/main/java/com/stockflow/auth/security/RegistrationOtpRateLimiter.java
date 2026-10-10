package com.stockflow.auth.security;

import com.stockflow.common.exception.AppException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Giới hạn mỗi tiến trình; giới hạn 5 lần sai/mã và 5 mã/giờ được lưu riêng trong database. */
@Component
public class RegistrationOtpRateLimiter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 8192;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public RegistrationOtpRateLimiter() { this(Clock.systemUTC()); }
    RegistrationOtpRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void checkVerify(String ip, String email) {
        check("verify:ip:" + ip, 30);
        check("verify:email:" + normalize(email), 10);
    }

    public synchronized void checkResend(String ip, String email) {
        check("resend:ip:" + ip, 10);
        check("resend:email:" + normalize(email), 5);
    }

    private void check(String key, int limit) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || now >= window.expiresAt) {
            if (windows.size() >= MAX_KEYS) {
                windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt);
                if (windows.size() >= MAX_KEYS) throw throttled();
            }
            window = new Window(now + WINDOW_MS);
            windows.put(key, window);
        }
        if (window.requests >= limit) throw throttled();
        window.requests++;
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private AppException throttled() {
        return new AppException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu OTP. Vui lòng thử lại sau một phút.");
    }
    private static class Window {
        private final long expiresAt;
        private int requests;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
