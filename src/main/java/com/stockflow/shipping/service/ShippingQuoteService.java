package com.stockflow.shipping.service;

import com.stockflow.common.config.GhnProperties;
import com.stockflow.common.exception.*;
import com.stockflow.shipping.client.GhnClient;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ShippingQuoteService {
    public static final int CHECKOUT_WEIGHT = 500;
    private final GhnClient client;
    private final GhnProperties.Settings settings;
    private final WarehouseRepository warehouses;
    public ShippingQuoteService(GhnClient client, GhnProperties.Settings settings, WarehouseRepository warehouses) {
        this.client = client; this.settings = settings; this.warehouses = warehouses;
    }
    public BigDecimal quote(Long warehouseId, int district, String ward, int weight) {
        var warehouse = warehouses.findById(warehouseId).orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) throw new ConflictException("Warehouse is inactive");
        if (district <= 0 || ward == null || !ward.matches("[A-Za-z0-9_-]{1,20}") || weight < 1 || weight >= 20000)
            throw new BadRequestException("Invalid shipping destination or weight");
        if (client.getWards(district).stream().noneMatch(w -> w.code().equals(ward)))
            throw new BadRequestException("Ward does not belong to district");
        return client.calculateFee(settings.districtFor(warehouseId), district, ward, weight);
    }
}
