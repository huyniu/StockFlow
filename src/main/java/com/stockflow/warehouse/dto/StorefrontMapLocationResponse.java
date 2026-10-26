package com.stockflow.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Only explicitly published map details; never reads operational warehouse addresses. */
public record StorefrontMapLocationResponse(
        Long id, String code, String name, String address,
        double latitude, double longitude, boolean demo,
        @JsonProperty("warehouse_id") Long warehouseId) {}
