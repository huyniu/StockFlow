package com.stockflow.shipping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class GhnLocations {
    private GhnLocations() {}
    public record Province(@JsonProperty("ProvinceID") int id, @JsonProperty("ProvinceName") String name) {}
    public record District(@JsonProperty("DistrictID") int id, @JsonProperty("DistrictName") String name,
                           @JsonProperty("ProvinceID") int provinceId) {}
    public record Ward(@JsonProperty("WardCode") String code, @JsonProperty("WardName") String name,
                       @JsonProperty("DistrictID") int districtId) {}
}
