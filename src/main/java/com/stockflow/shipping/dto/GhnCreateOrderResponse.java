package com.stockflow.shipping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnCreateOrderResponse(int code, Data data) {
    public record Data(@JsonProperty("order_code") String orderCode) {}
}
