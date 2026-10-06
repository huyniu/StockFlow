package com.stockflow.order.api;

import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.service.OrderService;
import com.stockflow.shipping.service.GhnShippingService;
import com.stockflow.user.domain.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderFulfillmentController {
    private final GhnShippingService shipping;
    private final OrderService orders;
    public OrderFulfillmentController(GhnShippingService shipping, OrderService orders) {
        this.shipping = shipping; this.orders = orders;
    }
    @PostMapping("/{id}/ghn-ship")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'MANAGER', 'ADMIN')")
    public OrderResponse ship(@PathVariable Long id, @AuthenticationPrincipal User user) {
        shipping.createOrderForFulfillment(id);
        return orders.getOrder(id, user.getId());
    }
}
