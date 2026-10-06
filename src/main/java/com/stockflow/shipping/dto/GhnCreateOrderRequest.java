package com.stockflow.shipping.dto;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonValue;

/** orderId is internal; only the GHN payload is serialized. */
public record GhnCreateOrderRequest(Long orderId, @JsonValue Map<String, Object> payload) {}
