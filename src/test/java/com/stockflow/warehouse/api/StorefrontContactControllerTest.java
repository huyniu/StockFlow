package com.stockflow.warehouse.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StorefrontContactControllerTest {
    @Test void missingContactDoesNotLinkToAnotherShop() {
        assertThat(new StorefrontContactController("").contact()).containsEntry("zalo_url", "");
    }
    @Test void trimsAndAcceptsShopContact() {
        assertThat(new StorefrontContactController(" https://zalo.me/0900000000 ").contact())
                .containsEntry("zalo_url", "https://zalo.me/0900000000");
    }
    @Test void rejectsUnsafeOrWrongDomains() {
        for (String url : new String[]{"javascript:alert(1)", "https://zalo.me.evil.example/123",
                "https://evil.example/123", "https://user@zalo.me/123", "http://zalo.me/123", "not a url"})
            assertThat(new StorefrontContactController(url).contact()).containsEntry("zalo_url", "");
    }
}
