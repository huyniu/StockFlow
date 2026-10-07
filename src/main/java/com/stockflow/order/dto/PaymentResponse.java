package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.domain.Payment;
import com.stockflow.order.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(String method, PaymentStatus status, BigDecimal amount,
        @JsonProperty("paid_at") Instant paidAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getMethod(), payment.getStatus(), payment.getAmount(), payment.getPaidAt());
    }
}
