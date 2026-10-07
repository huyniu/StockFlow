package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import com.stockflow.order.domain.Shipment;
import com.stockflow.order.dto.ShipmentResponse;
import org.junit.jupiter.api.Test;

class ShipmentModeTest {
    @Test void manuallyPackedShipmentDoesNotClaimRealCarrierPickup() {
        assertThat(ShipmentResponse.from(new Shipment(1L,"SF-1")).carrierMode()).isEqualTo("MANUAL");
    }
    @Test void modeSurvivesShippingDeliveryAndReturn() {
        var shipment = new Shipment(1L,"SF-1"); shipment.setCarrierMode("GHN_SANDBOX");
        shipment.ship("TEST-1", java.time.Instant.now()); shipment.deliver(java.time.Instant.now()); shipment.receiveReturn();
        assertThat(ShipmentResponse.from(shipment).carrierMode()).isEqualTo("GHN_SANDBOX");
    }
}
