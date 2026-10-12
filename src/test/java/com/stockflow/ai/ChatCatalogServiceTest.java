package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stockflow.ai.service.ChatCatalogService;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ChatCatalogServiceTest {
    private final ProductService products = mock(ProductService.class);
    private final ChatCatalogService catalog = new ChatCatalogService(products);

    static ProductResponse product(BigDecimal minPrice, BigDecimal maxPrice) {
        return new ProductResponse(42L, "PHONE-17", "IPHONE 17 PRO MAX", "Điện thoại",
                new BigDecimal("39990000"), "ACTIVE", Instant.EPOCH, null, null, List.of(),
                1L, null, null, List.of(), List.of(), null, List.of(), minPrice, maxPrice);
    }

    private void catalogPage(String query, List<ProductResponse> rows) {
        when(products.listProducts(isNull(), eq(ProductStatus.ACTIVE), eq(query), isNull(), isNull(),
                isNull(), eq(true), any(Pageable.class))).thenReturn(new PageImpl<>(rows));
    }

    @ParameterizedTest
    @ValueSource(strings = {"giá ip17", "giá IP 17", "giá iPhone17", "muốn mua ip17"})
    void aliasesQuoteCurrentPublicCatalog(String question) {
        catalogPage("iphone 17", List.of(product(new BigDecimal("34990000"), new BigDecimal("34990000"))));
        assertThat(catalog.answer(question)).hasValueSatisfying(reply ->
                assertThat(reply).contains("IPHONE 17 PRO MAX: 34.990.000 ₫", "/san-pham/42")
                        .doesNotContain("39.990.000", "Cảm ơn bạn đã nhắn tin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"giá ip17 promax", "giá iphone 17 pro max"})
    void quotesVariantStartingPriceAndKeepsModelQualifier(String question) {
        catalogPage("iphone 17 pro max", List.of(product(new BigDecimal("34990000"), new BigDecimal("39990000"))));
        assertThat(catalog.answer(question)).hasValueSatisfying(reply ->
                assertThat(reply).contains("từ 34.990.000 ₫"));
    }

    @Test
    void absentProductDoesNotInventPrice() {
        catalogPage("iphone 99", List.of());
        assertThat(catalog.answer("giá ip99")).hasValueSatisfying(reply ->
                assertThat(reply).contains("chưa tìm thấy", "iphone 99").doesNotContain("₫"));
    }

    @Test
    void otherPriceQueriesUseTheirProductName() {
        catalogPage("laptop asus", List.of());
        assertThat(catalog.answer("giá laptop Asus?")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"xin chào", "bảo hành ip17", "đổi trả iphone 17", "giá", "ip170"})
    void unrelatedOrIncompleteQuestionsDoNotQueryProducts(String question) {
        assertThat(catalog.answer(question)).isEmpty();
        verifyNoInteractions(products);
    }
}
