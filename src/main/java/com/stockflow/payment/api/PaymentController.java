package com.stockflow.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.common.config.VNPayConfig;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.payment.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
public class PaymentController {
    private final VNPayService service;
    private final VNPayConfig.Properties config;

    public PaymentController(VNPayService service, VNPayConfig.Properties config) {
        this.service = service;
        this.config = config;
    }

    public record CreatePaymentRequest(@JsonProperty("order_id") @NotNull @Positive Long orderId) {}

    @PostMapping("/create")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, String> create(@Valid @RequestBody CreatePaymentRequest body, HttpServletRequest request) {
        return Map.of("payment_url", service.createPaymentUrl(body.orderId(), request));
    }

    @GetMapping("/return")
    public ResponseEntity<Void> handleReturn(@RequestParam MultiValueMap<String, String> parameters) {
        Map<String, String> single = new HashMap<>();
        parameters.forEach((key, values) -> {
            if (values.size() != 1) throw new BadRequestException("Tham số VNPay không được lặp.");
            single.put(key, values.get(0));
        });
        var result = service.handleReturn(single);
        String redirect = config.storefrontUrl() + "#orders?payment_status="
                + (result.success() ? "success" : "failed") + "&order_id=" + result.orderId();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect)).build();
    }
}
