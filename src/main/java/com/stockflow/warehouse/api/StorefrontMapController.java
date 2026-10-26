package com.stockflow.warehouse.api;

import com.stockflow.warehouse.dto.StorefrontMapLocationResponse;
import com.stockflow.warehouse.service.StorefrontMapService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorefrontMapController {
    private final StorefrontMapService locations;

    public StorefrontMapController(StorefrontMapService locations) {
        this.locations = locations;
    }

    @GetMapping("/api/v1/storefront/locations")
    @Operation(summary = "Xem các địa điểm bản đồ được công khai; demo chỉ là vị trí minh họa")
    public List<StorefrontMapLocationResponse> publicLocations() {
        return locations.publicLocations();
    }
}
