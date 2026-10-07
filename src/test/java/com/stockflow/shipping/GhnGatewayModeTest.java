package com.stockflow.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import com.stockflow.common.config.GhnProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GhnGatewayModeTest {
    @Test void strictModeOnSandboxDoesNotClaimRealPickup() {
        var settings = new GhnProperties.Settings("https://dev-online-gateway.ghn.vn/shiip/public-api/", "token",1,"",1450,Map.of(),true);
        assertThat(settings.production()).isTrue();
        assertThat(settings.liveGateway()).isFalse();
    }
    @Test void liveGatewayIsRecognizedWithoutDependingOnFlag() {
        var settings = new GhnProperties.Settings("https://online-gateway.ghn.vn/shiip/public-api/", "token",1,"",1450,Map.of(),false);
        assertThat(settings.liveGateway()).isTrue();
        assertThat(settings.production()).isTrue();
    }
}
