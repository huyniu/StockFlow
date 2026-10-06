package com.stockflow.order;

import com.stockflow.common.config.GhnProperties;
import com.stockflow.shipping.client.GhnClient;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GhnLocationClientTest {
    @Test void realHttpContractUsesParentIdsAndFeePayload() throws Exception {
        var feeBody = new AtomicReference<String>();
        var districtQuery = new AtomicReference<String>();
        var wardQuery = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shiip/public-api/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            assertThat(exchange.getRequestHeaders().getFirst("Token")).isEqualTo("test-token");
            String data;
            if (path.endsWith("province")) data = "[{\"ProvinceID\":201,\"ProvinceName\":\"Ha Noi\"}]";
            else if (path.endsWith("district")) {
                districtQuery.set(exchange.getRequestURI().getQuery());
                data = "[{\"DistrictID\":1450,\"DistrictName\":\"Nam Tu Liem\"}]";
            } else if (path.endsWith("ward")) {
                wardQuery.set(exchange.getRequestURI().getQuery());
                data = "[{\"WardCode\":\"20907\",\"WardName\":\"Me Tri\"}]";
            } else {
                feeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                data = "{\"total\":45678}";
            }
            byte[] bytes = ("{\"code\":200,\"data\":" + data + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            var client = client(server);
            assertThat(client.getProvinces().get(0).id()).isEqualTo(201);
            assertThat(client.getDistricts(201).get(0).id()).isEqualTo(1450);
            assertThat(client.getWards(1450).get(0).code()).isEqualTo("20907");
            assertThat(districtQuery.get()).isEqualTo("province_id=201");
            assertThat(wardQuery.get()).isEqualTo("district_id=1450");
            assertThat(client.calculateFee(1442,1450,"20907",500)).isEqualByComparingTo("45678");
            var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(feeBody.get());
            assertThat(payload.path("from_district_id").asInt()).isEqualTo(1442);
            assertThat(payload.path("to_district_id").asInt()).isEqualTo(1450);
            assertThat(payload.path("to_ward_code").asText()).isEqualTo("20907");
            assertThat(payload.path("weight").asInt()).isEqualTo(500);
            assertThat(payload.path("length").asInt()).isEqualTo(15);
            assertThat(payload.path("service_type_id").asInt()).isEqualTo(2);
        } finally { server.stop(0); }
    }
    @Test void networkFailureFallsBackForAllFourMethods() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> { exchange.sendResponseHeaders(503,-1); exchange.close(); });
        server.start();
        try {
            var client=client(server);
            assertThat(client.getProvinces()).hasSize(3);
            assertThat(client.getDistricts(201).get(0).id()).isEqualTo(1450);
            assertThat(client.getWards(1450).get(0).code()).isEqualTo("20907");
            assertThat(client.calculateFee(1450,1450,"20907",500)).isEqualByComparingTo(new BigDecimal("30000"));
        } finally { server.stop(0); }
    }
    private GhnClient client(HttpServer server) {
        return new GhnClient(new GhnProperties.Settings("http://127.0.0.1:"+server.getAddress().getPort()+"/shiip/public-api/", "test-token", 123456, ""));
    }
}
