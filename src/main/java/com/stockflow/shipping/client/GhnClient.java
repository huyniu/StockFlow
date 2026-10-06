package com.stockflow.shipping.client;

import com.stockflow.common.config.GhnProperties;
import com.stockflow.shipping.dto.*;
import java.security.SecureRandom;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GhnClient {
    private static final Logger log = LoggerFactory.getLogger(GhnClient.class);
    private final GhnProperties.Settings settings;
    private final RestClient client;
    private final SecureRandom random = new SecureRandom();

    private boolean mockMode() {
        if (settings.production() && (settings.token() == null || settings.token().isBlank()
                || "MOCK_TOKEN".equals(settings.token().trim()))) throw new GhnUnavailableException();
        return settings.token() == null || settings.token().isBlank() || "MOCK_TOKEN".equals(settings.token().trim());
    }

    public boolean isTestMode() { return !settings.production(); }

    private void requireSimulationAllowed() {
        if (settings.production()) throw new GhnUnavailableException();
    }

    private com.fasterxml.jackson.databind.JsonNode requestData(String path, java.util.Map<String, Object> body) {
        var request = body == null ? client.get().uri(path) : client.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON).body(body);
        var result = request.header("Token", settings.token().trim())
                .header("ShopId", Integer.toString(settings.shopId())).retrieve()
                .body(com.fasterxml.jackson.databind.JsonNode.class);
        if (result == null || result.path("code").asInt() != 200 || result.path("data").isNull()
                || result.path("data").isMissingNode()) throw new org.springframework.web.client.RestClientException("Invalid GHN response");
        return result.path("data");
    }

    public java.util.List<GhnLocations.Province> getProvinces() {
        if (!mockMode()) try {
            var data = requestData("master-data/province", null);
            if (!data.isArray()) throw new org.springframework.web.client.RestClientException("Invalid GHN provinces");
            var values = new java.util.ArrayList<GhnLocations.Province>();
            data.forEach(p -> { if (p.path("ProvinceID").asInt() > 0 && !p.path("ProvinceName").asText().isBlank())
                values.add(new GhnLocations.Province(p.path("ProvinceID").asInt(), p.path("ProvinceName").asText())); });
            return values;
        } catch (org.springframework.web.client.RestClientException e) { log.warn("[GHN] Province API unavailable"); }
        requireSimulationAllowed();
        return java.util.List.of(new GhnLocations.Province(201, "Hà Nội"), new GhnLocations.Province(202, "TP. Hồ Chí Minh"), new GhnLocations.Province(203, "Đà Nẵng"));
    }

    public java.util.List<GhnLocations.District> getDistricts(int provinceId) {
        if (!mockMode()) try {
            var data = requestData("master-data/district?province_id=" + provinceId, null);
            if (!data.isArray()) throw new org.springframework.web.client.RestClientException("Invalid GHN districts");
            var values = new java.util.ArrayList<GhnLocations.District>();
            data.forEach(p -> { if ((!p.has("ProvinceID") || p.path("ProvinceID").asInt() == provinceId) && p.path("DistrictID").asInt() > 0)
                values.add(new GhnLocations.District(p.path("DistrictID").asInt(), p.path("DistrictName").asText(), provinceId)); });
            return values;
        } catch (org.springframework.web.client.RestClientException e) { log.warn("[GHN] District API unavailable"); }
        requireSimulationAllowed();
        return switch (provinceId) {
            case 201 -> java.util.List.of(new GhnLocations.District(1450, "Nam Từ Liêm", 201), new GhnLocations.District(1442, "Cầu Giấy", 201));
            case 202 -> java.util.List.of(new GhnLocations.District(1443, "Quận 1", 202));
            case 203 -> java.util.List.of(new GhnLocations.District(1526, "Hải Châu", 203));
            default -> java.util.List.of();
        };
    }

    public java.util.List<GhnLocations.Ward> getWards(int districtId) {
        if (!mockMode()) try {
            var data = requestData("master-data/ward?district_id=" + districtId, null);
            if (!data.isArray()) throw new org.springframework.web.client.RestClientException("Invalid GHN wards");
            var values = new java.util.ArrayList<GhnLocations.Ward>();
            data.forEach(p -> { if (!p.path("WardCode").asText().isBlank())
                values.add(new GhnLocations.Ward(p.path("WardCode").asText(), p.path("WardName").asText(), districtId)); });
            return values;
        } catch (org.springframework.web.client.RestClientException e) { log.warn("[GHN] Ward API unavailable"); }
        requireSimulationAllowed();
        return switch (districtId) {
            case 1450 -> java.util.List.of(new GhnLocations.Ward("20907", "Mễ Trì", 1450), new GhnLocations.Ward("20908", "Mỹ Đình", 1450));
            case 1442 -> java.util.List.of(new GhnLocations.Ward("20101", "Dịch Vọng", 1442));
            case 1443 -> java.util.List.of(new GhnLocations.Ward("20301", "Bến Nghé", 1443));
            case 1526 -> java.util.List.of(new GhnLocations.Ward("30101", "Hải Châu I", 1526));
            default -> java.util.List.of();
        };
    }

    public java.math.BigDecimal calculateFee(int fromDistrictId, int toDistrictId, String ward, int weight) {
        if (!mockMode()) try {
            var data = requestData("v2/shipping-order/fee", java.util.Map.of("service_type_id", 2,
                    "from_district_id", fromDistrictId, "to_district_id", toDistrictId, "to_ward_code", ward,
                    "weight", weight, "length", 15, "width", 15, "height", 10));
            if (!data.path("total").isNumber()) throw new org.springframework.web.client.RestClientException("Invalid GHN fee");
            var fee = data.path("total").decimalValue();
            if (fee.signum() < 0 || fee.compareTo(new java.math.BigDecimal("9999999999.99")) > 0)
                throw new org.springframework.web.client.RestClientException("Invalid GHN fee");
            return fee.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (org.springframework.web.client.RestClientException | ArithmeticException e) { log.warn("[GHN] Fee API unavailable"); }
        requireSimulationAllowed();
        return new java.math.BigDecimal("30000.00");
    }

    public GhnClient(GhnProperties.Settings settings) {
        this.settings = settings;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory)
                .baseUrl(settings.baseUrl()).build();
    }

    public GhnCreateOrderResponse createShippingOrder(GhnCreateOrderRequest request) {
        mockMode(); // Reject missing/test credentials in production before doing anything.
        if (settings.token() != null && !settings.token().isBlank()
                && !"MOCK_TOKEN".equals(settings.token().trim())) {
            try {
                var result = client.post().uri("v2/shipping-order/create")
                        .header("Token", settings.token().trim())
                        .header("ShopId", Integer.toString(settings.shopId()))
                        .contentType(MediaType.APPLICATION_JSON).body(request.payload())
                        .retrieve().body(GhnCreateOrderResponse.class);
                String code = result == null || result.data() == null ? null : result.data().orderCode();
                if (result != null && result.code() == 200 && code != null
                        && code.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) return result;
                log.warn("[GHN] Invalid response for order {}", request.orderId());
            } catch (org.springframework.web.client.RestClientException exception) {
                // Do not log request headers, customer addresses or response bodies containing PII.
                log.warn("[GHN] API unavailable for order {} ({})",
                        request.orderId(), exception.getClass().getSimpleName());
            }
        }
        requireSimulationAllowed();
        String code = "GHN_HAN_" + request.orderId() + "_" + String.format("%04d", random.nextInt(10000));
        log.info("[GHN] Simulated tracking {}", code);
        return new GhnCreateOrderResponse(200, new GhnCreateOrderResponse.Data(code));
    }
}
