package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.config.GeminiHttpConfiguration;
import com.stockflow.ai.config.GeminiProperties;
import com.stockflow.ai.service.ChatCatalogService;
import com.stockflow.ai.service.GeminiChatService;
import com.stockflow.catalog.service.ProductService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@ExtendWith(OutputCaptureExtension.class)
class GeminiChatServiceTest {
    private final ProductService products = mock(ProductService.class);
    private final ChatCatalogService catalog = mock(ChatCatalogService.class);
    private GeminiChatService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        var properties = new GeminiProperties();
        properties.setApiKey("test-api-key");
        var client = new GeminiHttpConfiguration().geminiRestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
        service = new GeminiChatService(properties, products, catalog, new ObjectMapper(), client);
        when(products.listProducts(isNull(), any(), isNull(), isNull(), isNull(), isNull(), eq(true),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void providerUsesHeaderCredentialAndReadsAllNonThoughtText() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[
                            {"text":"private reasoning", "thought":true},
                            {"text":"Mình có thể "},{"text":"tư vấn cho bạn."}
                        ]}}]}
                        """, MediaType.APPLICATION_JSON));
        var response = service.chat("cần hỗ trợ");
        assertThat(response.getReply()).isEqualTo("Mình có thể tư vấn cho bạn.");
        assertThat(response.isAiGenerated()).isTrue();
        server.verify();
    }

    @Test
    void providerFailureIsHonestAndDoesNotLogCredentials(CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"test-api-key\"}"));
        var response = service.chat("cần hỗ trợ");
        assertThat(response.getReply()).contains("chưa kết nối được trợ lý AI");
        assertThat(response.isAiGenerated()).isFalse();
        assertThat(output).contains("HTTP 404").doesNotContain("test-api-key");
        server.verify();
    }

    @Test
    void currentPriceDoesNotRequireAnExternalAiCall() {
        when(catalog.answer("giá ip17")).thenReturn(Optional.of("IPHONE 17 PRO MAX: 34.990.000 ₫"));
        var response = service.chat("giá ip17");
        assertThat(response.getReply()).contains("34.990.000 ₫");
        assertThat(response.isAiGenerated()).isFalse();
        verifyNoInteractions(products);
        server.verify();
    }

    @Test
    void emptyCandidateDoesNotPretendToBeAnAiAnswer() {
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        assertThat(service.chat("cần hỗ trợ").isAiGenerated()).isFalse();
        server.verify();
    }

    @Test
    void catalogFailureDoesNotAskAnAiToInventPrice() {
        when(catalog.answer("giá ip17")).thenThrow(new IllegalStateException("Catalog unavailable"));
        var response = service.chat("giá ip17");
        assertThat(response.getReply()).contains("chưa đọc được giá từ catalog");
        assertThat(response.isAiGenerated()).isFalse();
        verifyNoInteractions(products);
        server.verify();
    }
}
