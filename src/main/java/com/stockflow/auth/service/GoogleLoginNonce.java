package com.stockflow.auth.service;

import com.stockflow.common.exception.AppException;
import com.stockflow.common.exception.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Short-lived, one-use challenge bound to an HttpOnly browser cookie and Google's signed nonce. */
@Component
public class GoogleLoginNonce {
    private final ConcurrentHashMap<String,Instant> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    public synchronized String issue() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (pending.size() >= 2000) throw new AppException(HttpStatus.TOO_MANY_REQUESTS,"Vui lòng thử lại sau.");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(value,now.plusSeconds(300));
        return value;
    }
    public void consume(String value) {
        Instant expires = value == null ? null : pending.remove(value);
        if (expires == null || !expires.isAfter(Instant.now())) throw new UnauthorizedException("Phiên đăng nhập Google đã hết hạn. Vui lòng mở lại đăng nhập.");
    }
}
