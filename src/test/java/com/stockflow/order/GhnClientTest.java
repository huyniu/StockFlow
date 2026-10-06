package com.stockflow.order;

import com.stockflow.common.config.GhnProperties;
import com.stockflow.shipping.client.GhnClient;
import com.stockflow.shipping.dto.GhnCreateOrderRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GhnClientTest {
    @Test void sendsRealRequestWithHeadersAndReadsOrderCode() throws Exception {
        var headers = new AtomicReference<com.sun.net.httpserver.Headers>();
        var body = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shiip/public-api/v2/shipping-order/create", exchange -> {
            headers.set(exchange.getRequestHeaders()); method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"code\":200,\"data\":{\"order_code\":\"GHNREAL123\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = client(server);
            assertThat(client.createShippingOrder(new GhnCreateOrderRequest(42L, Map.of("client_order_code", "STOCKFLOW-42", "cod_amount", 0))).data().orderCode()).isEqualTo("GHNREAL123");
            assertThat(method.get()).isEqualTo("POST");
            assertThat(headers.get().getFirst("Token")).isEqualTo("test-token");
            assertThat(headers.get().getFirst("ShopId")).isEqualTo("123456");
            assertThat(headers.get().getFirst("Content-Type")).startsWith("application/json");
            assertThat(body.get()).contains("STOCKFLOW-42", "\"cod_amount\":0").doesNotContain("orderId", "payload");
        } finally { server.stop(0); }
    }
    @Test void apiErrorFallsBack() throws Exception { checkFallback(503, "{}"); }
    @Test void malformedResponseFallsBack() throws Exception { checkFallback(200, "not-json"); }
    @Test void businessFailureFallsBack() throws Exception { checkFallback(200, "{\"code\":400,\"data\":null}"); }
    private void checkFallback(int status, String response) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shiip/public-api/v2/shipping-order/create", exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            assertThat(client(server).createShippingOrder(new GhnCreateOrderRequest(42L, Map.of())).data().orderCode()).matches("GHN_HAN_42_[0-9]{4}");
        } finally { server.stop(0); }
    }
    private GhnClient client(HttpServer server) {
        return new GhnClient(new GhnProperties.Settings("http://127.0.0.1:"+server.getAddress().getPort()+"/shiip/public-api/", "test-token", 123456, ""));
    }
}
