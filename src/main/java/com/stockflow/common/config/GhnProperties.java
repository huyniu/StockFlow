package com.stockflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GhnProperties.Settings.class)
public class GhnProperties {
    @ConfigurationProperties(prefix = "ghn")
    public record Settings(String baseUrl, String token, int shopId, String senderPhone,
                           Integer fromDistrictId, java.util.Map<Long, Integer> warehouseDistricts, Boolean production) {
        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public Settings {
            // The live gateway must never fall back to simulated shipping, even if misconfigured.
            boolean liveGateway = baseUrl != null && "online-gateway.ghn.vn".equalsIgnoreCase(java.net.URI.create(baseUrl).getHost());
            production = liveGateway || Boolean.TRUE.equals(production);
            fromDistrictId = fromDistrictId == null ? 1450 : fromDistrictId;
            warehouseDistricts = warehouseDistricts == null ? java.util.Map.of() : java.util.Map.copyOf(warehouseDistricts);
        }
        public Settings(String baseUrl, String token, int shopId, String senderPhone) {
            this(baseUrl, token, shopId, senderPhone, 1450, java.util.Map.of(), false);
        }
        public Settings(String baseUrl, String token, int shopId, String senderPhone,
                        Integer fromDistrictId, java.util.Map<Long, Integer> warehouseDistricts) {
            this(baseUrl, token, shopId, senderPhone, fromDistrictId, warehouseDistricts, false);
        }
        public int districtFor(Long warehouseId) { return warehouseDistricts.getOrDefault(warehouseId, fromDistrictId); }
    }
}
