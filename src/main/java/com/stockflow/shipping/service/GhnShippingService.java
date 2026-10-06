package com.stockflow.shipping.service;

import com.stockflow.common.config.GhnProperties;
import com.stockflow.common.exception.*;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.ShipOrderRequest;
import com.stockflow.order.service.OrderService;
import com.stockflow.shipping.client.GhnClient;
import com.stockflow.shipping.dto.GhnCreateOrderRequest;
import com.stockflow.user.domain.User;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GhnShippingService {
    private final OrderService fulfillment;
    private final WarehouseRepository warehouses;
    private final GhnClient client;
    private final GhnProperties.Settings settings;
    private final JdbcTemplate jdbc;

    public GhnShippingService(OrderService fulfillment,
            WarehouseRepository warehouses, GhnClient client, GhnProperties.Settings settings, JdbcTemplate jdbc) {
        this.fulfillment = fulfillment; this.warehouses = warehouses;
        this.client = client; this.settings = settings; this.jdbc = jdbc;
    }

    @Transactional
    public String createOrderForFulfillment(Long orderId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) throw new UnauthorizedException("Login required");
        // Validate live database permissions and fulfillment invariants before contacting GHN.
        var order = fulfillment.prepareCarrierShipment(orderId, user.getId());
        var existing = jdbc.queryForList("SELECT tracking_code FROM shipping_dispatch_events WHERE order_id = ?", String.class, orderId);
        if (!existing.isEmpty()) return existing.get(0);
        if (order.getStatus() != OrderStatus.PACKED) throw new ConflictException("Order must be PACKED");
        var warehouse = warehouses.findById(order.getWarehouseId()).orElseThrow();
        var delivery = order.getDelivery();
        if (delivery == null) throw new ConflictException("Order has no delivery details");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("client_order_code", "STOCKFLOW-" + orderId);
        payload.put("from_name", warehouse.getName());
        if (settings.senderPhone() != null && !settings.senderPhone().isBlank()) payload.put("from_phone", settings.senderPhone());
        payload.put("from_address", warehouse.getAddress());
        payload.put("to_name", delivery.getRecipientName());
        payload.put("to_phone", delivery.getRecipientPhone());
        payload.put("to_address", delivery.getAddress());
        if (delivery.getToDistrictId() != null && delivery.getToWardCode() != null) {
            payload.put("to_district_id", delivery.getToDistrictId());
            payload.put("to_ward_code", delivery.getToWardCode());
            payload.put("from_district_id", settings.districtFor(order.getWarehouseId()));
        } else addAddressParts(payload, "to", delivery.getAddress());
        addAddressParts(payload, "from", warehouse.getAddress());
        // Full free-text addresses are kept intact; GHN may resolve or reject them, then fallback applies.
        payload.put("payment_type_id", 1);
        payload.put("service_type_id", 2);
        payload.put("required_note", "KHONGCHOXEMHANG");
        payload.put("cod_amount", 0); // StockFlow orders have already been paid.
        payload.put("weight", ShippingQuoteService.CHECKOUT_WEIGHT); payload.put("length", 15); payload.put("width", 15); payload.put("height", 10);
        payload.put("content", "StockFlow order " + order.getOrderCode());
        if (delivery.getNote() != null) payload.put("note", delivery.getNote());
        String tracking = client.createShippingOrder(new GhnCreateOrderRequest(orderId, payload)).data().orderCode();
        fulfillment.shipOrder(orderId, new ShipOrderRequest(tracking), user.getId());
        jdbc.update("INSERT INTO shipping_dispatch_events (order_id, tracking_code, performed_by, event_type) VALUES (?, ?, ?, 'ORDER_DISPATCH')",
                orderId, tracking, user.getId());
        return tracking;
    }

    /** Accept full legacy addresses: street, ward, district, province. Never invent a destination. */
    private void addAddressParts(java.util.Map<String, Object> payload, String prefix, String address) {
        String[] parts = address.split(",");
        if (parts.length >= 4) {
            payload.put(prefix + "_ward_name", parts[parts.length - 3].trim());
            payload.put(prefix + "_district_name", parts[parts.length - 2].trim());
            payload.put(prefix + "_province_name", parts[parts.length - 1].trim());
        } else if (parts.length == 3) {
            payload.put(prefix + "_ward_name", parts[1].trim());
            payload.put(prefix + "_province_name", parts[2].trim());
            payload.put("is_new_" + prefix + "_address", true);
        }
    }
}
