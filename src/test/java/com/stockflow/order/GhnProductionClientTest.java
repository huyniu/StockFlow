package com.stockflow.order;

import com.stockflow.common.config.GhnProperties;
import com.stockflow.shipping.client.*;
import com.stockflow.shipping.dto.GhnCreateOrderRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GhnProductionClientTest {
    @Test void liveGatewayCannotEnableSimulation() {
        var settings = new GhnProperties.Settings("https://online-gateway.ghn.vn/shiip/public-api/", "MOCK_TOKEN", 1, "");
        assertThat(settings.production()).isTrue();
        var client = new GhnClient(settings);
        assertThatThrownBy(client::getProvinces).isInstanceOf(GhnUnavailableException.class);
        assertThatThrownBy(() -> client.createShippingOrder(new GhnCreateOrderRequest(1L, Map.of())))
                .isInstanceOf(GhnUnavailableException.class);
    }

    @Test void productionApiFailureNeverReturnsSampleLocationsFeesOrTracking() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            var client = new GhnClient(new GhnProperties.Settings("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    "test-token", 1, "", 1450, Map.of(), true));
            assertThat(client.isTestMode()).isFalse();
            assertThatThrownBy(client::getProvinces).isInstanceOf(GhnUnavailableException.class);
            assertThatThrownBy(() -> client.getDistricts(201)).isInstanceOf(GhnUnavailableException.class);
            assertThatThrownBy(() -> client.getWards(1450)).isInstanceOf(GhnUnavailableException.class);
            assertThatThrownBy(() -> client.calculateFee(1450, 1450, "20907", 500)).isInstanceOf(GhnUnavailableException.class);
            assertThatThrownBy(() -> client.createShippingOrder(new GhnCreateOrderRequest(1L, Map.of())))
                    .isInstanceOf(GhnUnavailableException.class);
        } finally { server.stop(0); }
    }
}
