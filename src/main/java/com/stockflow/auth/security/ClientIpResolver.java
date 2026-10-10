package com.stockflow.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/** Chỉ đọc chuỗi forwarded khi peer socket là proxy được cấu hình rõ ràng. Không phân giải DNS. */
@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trustedProxies = new ArrayList<>();

    public ClientIpResolver(@Value("${app.security.trusted-proxy-cidrs:}") String cidrs,
            @Value("${server.forward-headers-strategy:none}") String forwardingStrategy) {
        if (!"none".equalsIgnoreCase(forwardingStrategy.trim())) {
            throw new IllegalArgumentException("OTP IP limits require server.forward-headers-strategy=none and an explicit proxy allowlist.");
        }
        for (String entry : cidrs.split(",")) {
            String cidr = entry.trim();
            if (cidr.isEmpty()) continue;
            String[] parts = cidr.split("/", -1);
            String address = literal(parts[0]);
            if (address == null || parts.length > 2) throw new IllegalArgumentException("Invalid trusted proxy CIDR");
            if (parts.length == 2) {
                int bits = Integer.parseInt(parts[1]);
                if (bits < 1 || bits > (address.contains(":") ? 128 : 32)) {
                    throw new IllegalArgumentException("Trusted proxy CIDR must not trust all addresses");
                }
            }
            trustedProxies.add(new IpAddressMatcher(cidr));
        }
    }

    public String resolve(HttpServletRequest request) {
        String peer = literal(request.getRemoteAddr());
        if (peer == null) return "unknown-peer";
        if (!trusted(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 1024) return peer;
        String[] chain = forwarded.split(",", -1);
        if (chain.length > 20) return peer;
        List<String> addresses = new ArrayList<>();
        for (String entry : chain) {
            String address = literal(entry.trim());
            if (address == null) return peer;
            addresses.add(address);
        }
        // Đọc từ proxy gần ứng dụng nhất, dừng ở hop không được tin cậy đầu tiên.
        String current = peer;
        for (int index = addresses.size() - 1; index >= 0 && trusted(current); index--) {
            current = addresses.get(index);
        }
        return current;
    }

    private boolean trusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }

    private static String literal(String value) {
        if (value == null || value.length() > 45) return null;
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:.]+")) return null;
        } else {
            if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
            for (String part : value.split("\\.")) if (Integer.parseInt(part) > 255) return null;
        }
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException exception) { return null; }
    }
}
