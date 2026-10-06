package com.stockflow.warehouse.api;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorefrontContactController {
    private final String zaloUrl;

    public StorefrontContactController(@Value("${stockflow.contact.zalo-url:}") String configuredUrl) {
        this.zaloUrl = validatedUrl(configuredUrl);
    }

    static String validatedUrl(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            var uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"zalo.me".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getRawQuery() != null
                    || uri.getFragment() != null || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_-]+/?")) return "";
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) { return ""; }
    }

    @GetMapping("/api/v1/storefront/contact")
    public Map<String, String> contact() { return Map.of("zalo_url", zaloUrl); }
}
