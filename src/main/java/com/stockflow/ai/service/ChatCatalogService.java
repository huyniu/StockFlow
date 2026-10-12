package com.stockflow.ai.service;

import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.service.ProductService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Quotes the public catalog rather than letting a model invent current prices. */
@Service
public class ChatCatalogService {
    private static final Pattern IPHONE = Pattern.compile(
            "\\b(?:ip|iphone)[\\s-]*(\\d{1,2})(?:\\s*(pro\\s*max|pro|plus|air|mini))?\\b");
    private final ProductService products;

    public ChatCatalogService(ProductService products) {
        this.products = products;
    }

    public Optional<String> answer(String message) {
        String normalized = Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
        if (normalized.contains("bao hanh") || normalized.contains("doi tra")) return Optional.empty();
        var iphone = IPHONE.matcher(normalized);
        String query;
        if (iphone.find()) {
            query = "iphone " + iphone.group(1);
            if (iphone.group(2) != null) query += " " + iphone.group(2)
                    .replaceAll("pro\\s*max", "pro max").replaceAll("\\s+", " ");
        } else if (normalized.matches(".*\\b(?:gia|bao nhieu|muon mua)\\b.*")) {
            query = message.toLowerCase(Locale.ROOT)
                    .replaceAll("\\b(?:giá|gia|bao nhiêu|bao nhieu|muốn mua|muon mua|cho tôi|cho toi|là|la|của|cua)\\b", " ")
                    .replaceAll("[?!.]", " ").replaceAll("\\s+", " ").trim();
            if (query.length() < 3 || query.length() > 100) return Optional.empty();
        } else {
            return Optional.empty();
        }

        var page = products.listProducts(null, ProductStatus.ACTIVE, query, null, null, null, true,
                PageRequest.of(0, 5, Sort.by("unitPrice").ascending()));
        if (page.isEmpty()) {
            return Optional.of("Mình chưa tìm thấy sản phẩm đang bán phù hợp với “" + query
                    + "” trong catalog. Bạn kiểm tra lại tên hoặc chọn sản phẩm trên website nhé.");
        }
        StringBuilder reply = new StringBuilder("Giá hiện tại trên StockFlow:\n");
        for (ProductResponse product : page) {
            BigDecimal price = product.minPrice() == null ? product.unitPrice() : product.minPrice();
            boolean range = product.maxPrice() != null && product.maxPrice().compareTo(price) > 0;
            reply.append("- ").append(product.name()).append(": ")
                    .append(range ? "từ " : "").append(formatPrice(price)).append("\n")
                    .append("  Xem chi tiết: /san-pham/").append(product.id()).append("\n");
        }
        reply.append("Bạn mở trang sản phẩm để chọn phiên bản, màu và kiểm tra hàng tại chi nhánh.");
        return Optional.of(reply.toString());
    }

    static String formatPrice(BigDecimal price) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
    }
}
