package com.stockflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GhnProperties.Settings.class)
public class GhnProperties {
    @ConfigurationProperties(prefix = "ghn")
    public record Settings(String baseUrl, String token, int shopId, String senderPhone,
                           Integer fromDistrictId, java.util.Map<Long, Integer> warehouseDistricts) {
        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public Settings {
            fromDistrictId = fromDistrictId == null ? 1450 : fromDistrictId;
            warehouseDistricts = warehouseDistricts == null ? java.util.Map.of() : java.util.Map.copyOf(warehouseDistricts);
        }
        public Settings(String baseUrl, String token, int shopId, String senderPhone) {
            this(baseUrl, token, shopId, senderPhone, 1450, java.util.Map.of());
        }
        public int districtFor(Long warehouseId) { return warehouseDistricts.getOrDefault(warehouseId, fromDistrictId); }
    }
}
