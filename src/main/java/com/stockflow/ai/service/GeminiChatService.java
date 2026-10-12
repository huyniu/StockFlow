package com.stockflow.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.config.GeminiProperties;
import com.stockflow.ai.dto.AiChatResponse;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.service.ProductService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.data.domain.Sort;

import java.util.*;

@Service
public class GeminiChatService {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final GeminiProperties properties;
    private final ProductService products;
    private final ChatCatalogService catalog;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiChatService(GeminiProperties properties, ProductService products, ChatCatalogService catalog,
            ObjectMapper objectMapper, @Qualifier("geminiRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.products = products;
        this.catalog = catalog;
        this.objectMapper = objectMapper;

        this.restTemplate = restTemplate;
    }

    public AiChatResponse chat(String userMessage) {
        try {
            var answer = catalog.answer(userMessage);
            if (answer.isPresent()) return new AiChatResponse(answer.get(), false);
        } catch (RuntimeException exception) {
            log.warn("[AI] Catalog lookup unavailable ({})", exception.getClass().getSimpleName());
            return new AiChatResponse("Mình chưa đọc được giá từ catalog. Bạn thử lại sau hoặc liên hệ cửa hàng nhé.", false);
        }
        if (!properties.isConfigured()) {
            log.warn("[AI] Gemini API Key chua duoc cau hinh, chuyen sang phan hoi thong minh du phong");
            return fallbackReply(userMessage);
        }

        try {
            String productContext = buildProductContext();
            String fullPrompt = buildSystemPrompt(productContext, userMessage);

            String url = String.format(GEMINI_BASE_URL, properties.getModel());

            Map<String, Object> textPart = Map.of("text", fullPrompt);
            Map<String, Object> partsObj = Map.of("parts", List.of(textPart));
            Map<String, Object> requestBody = Map.of("contents", List.of(partsObj),
                    "generationConfig", Map.of("maxOutputTokens", 1024));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", properties.getApiKey());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                StringBuilder answer = new StringBuilder();
                for (JsonNode part : root.at("/candidates/0/content/parts")) {
                    if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                        answer.append(part.path("text").asText());
                    }
                }
                if (!answer.toString().isBlank()) {
                    return new AiChatResponse(answer.toString().trim(), true);
                }
            }
        } catch (HttpStatusCodeException exception) {
            // Provider messages may contain request credentials; log only the status.
            log.warn("[AI] Gemini request failed with HTTP {}", exception.getStatusCode().value());
        } catch (Exception exception) {
            log.warn("[AI] Gemini request unavailable ({})", exception.getClass().getSimpleName());
        }

        return fallbackReply(userMessage);
    }

    private String buildProductContext() {
        try {
            var page = products.listProducts(null, ProductStatus.ACTIVE, null, null, null, null, true,
                    PageRequest.of(0, 25, Sort.by("id").ascending()));
            if (page.isEmpty()) return "Chưa có dữ liệu catalog để tư vấn.";
            StringBuilder sb = new StringBuilder();
            for (var p : page) {
                var price = p.minPrice() == null ? p.unitPrice() : p.minPrice();
                sb.append("- ").append(p.name()).append(" (giá từ: ")
                        .append(ChatCatalogService.formatPrice(price)).append(")")
                        .append(" [danh mục: ").append(p.categoryName()).append("]")
                        .append(" [trang sản phẩm: /san-pham/").append(p.id()).append("]");
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Chưa đọc được catalog. Không được tự suy đoán giá hoặc tồn kho.";
        }
    }

    private String buildSystemPrompt(String productContext, String userMessage) {
        return "Ban la Tro ly AI tu van cong nghe cua cua hang StockFlow Tech.\n"
             + "Duoi day la danh sach mot so san pham hien co tai cua hang:\n"
             + productContext + "\n\n"
             + "Thong tin ho tro khach:\n"
             + "- Giao hang toan quoc qua doi tac Giao Hang Nhanh (GHN).\n"
             + "- Ho tro thanh toan khi nhan hang (COD) hoac VNPay.\n"
             + "- Bao hanh va doi tra: huong dan khach xem chinh sach tren website hoac lien he cua hang; khong tu dua ra thoi han hay cam ket.\n\n"
             + "Yeu cau phan hoi:\n"
             + "- Lich su, than thien, suc tich bang tieng Viet.\n"
             + "- Goi y dung san pham trong danh muc phu hop voi nhu cau va tam gia cua khach.\n"
             + "- Khong tu tao gia, khuyen mai, ton kho hay tinh nang khong co trong du lieu. Gia tu phu thuoc phien ban va mau.\n"
             + "- Giu cau tra loi ro rang, de doc.\n\n"
             + "Khach hang hoi: " + userMessage;
    }

    private AiChatResponse fallbackReply(String userMessage) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        String reply;
        if (lower.matches("^(?:xin\\s+)?(?:chào|chao|hi|hello)\\b.*")) {
            reply = "Xin chào bạn! Mình là Trợ lý AI của StockFlow Tech. Bạn đang cần tìm điện thoại, laptop hay phụ kiện công nghệ gì để mình hỗ trợ ngay nhé?";
        } else if (lower.contains("bao hanh") || lower.contains("bảo hành") || lower.contains("doi tra") || lower.contains("đổi trả")) {
            reply = "Bạn có thể xem hướng dẫn bảo hành, đổi trả trên website hoặc liên hệ cửa hàng qua mục Liên hệ để được xác nhận chính sách cho sản phẩm của bạn.";
        } else if (lower.contains("van chuyen") || lower.contains("vận chuyển") || lower.contains("giao hang") || lower.contains("giao hàng") || lower.contains("ship")) {
            reply = "StockFlow dùng Giao Hàng Nhanh (GHN). Phí giao hàng được tính theo địa chỉ khi đặt đơn; bạn xem trạng thái và mã vận đơn trong tài khoản sau khi đơn được gửi.";
        } else if (lower.contains("iphone") || lower.contains("apple") || lower.contains("dien thoai") || lower.contains("điện thoại")) {
            reply = "Bạn có thể xem danh mục Điện thoại trên website, hoặc hỏi giá kèm tên máy cụ thể, ví dụ “giá ip17”, để mình tra giá từ catalog hiện tại.";
        } else if (lower.contains("laptop") || lower.contains("may tinh") || lower.contains("máy tính")) {
            reply = "Bạn có thể xem danh mục laptop trên website hoặc hỏi giá kèm tên máy cụ thể để mình tra catalog hiện tại.";
        } else {
            reply = "Mình chưa kết nối được trợ lý AI để tư vấn câu hỏi này. Bạn vẫn có thể hỏi giá kèm tên sản phẩm, ví dụ “giá ip17”, hoặc dùng Zalo và Gọi điện trong mục Liên hệ.";
        }
        return new AiChatResponse(reply, false);
    }
}
