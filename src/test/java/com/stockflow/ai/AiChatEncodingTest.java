package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.api.AiChatController;
import com.stockflow.ai.config.GeminiProperties;
import com.stockflow.ai.service.GeminiChatService;
import com.stockflow.catalog.service.ProductService;
import com.stockflow.ai.service.ChatCatalogService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

class AiChatEncodingTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ProductService products;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        products = mock(ProductService.class);
        // No API key: exercise the real fallback without Gemini or a database.
        var service = new GeminiChatService(new GeminiProperties(), products, mock(ChatCatalogService.class),
                mapper, new RestTemplate());
        mvc = MockMvcBuilders.standaloneSetup(new AiChatController(service)).build();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cần hỗ trợ | Mình chưa kết nối được trợ lý AI",
            "hello | Xin chào bạn!",
            "bảo hành | hướng dẫn bảo hành",
            "đổi trả | hướng dẫn bảo hành",
            "vận chuyển | Giao Hàng Nhanh",
            "giao hàng | Giao Hàng Nhanh",
            "điện thoại | Điện thoại",
            "máy tính | danh mục laptop"
    })
    void fallbackAndAccentedKeywordsSurviveJsonRoundTrip(String question, String expected) throws Exception {
        var response = mvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(mapper.writeValueAsBytes(Map.of("message", question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString(expected)))
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andReturn().getResponse();

        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("áº", "á»", "Æ", "Ä", "\uFFFD");
        verifyNoInteractions(products);
    }

    @Test
    void emptyQuestionReturnsReadableVietnamese() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reply").value("Vui lòng nhập câu hỏi để được trợ lý AI tư vấn."));
        verifyNoInteractions(products);
    }
}
