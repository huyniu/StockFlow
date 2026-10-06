package com.stockflow.shipping.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.shipping.client.GhnClient;
import com.stockflow.shipping.dto.GhnLocations;
import com.stockflow.shipping.service.ShippingQuoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class ShippingLocationController {
    private final GhnClient client;
    private final ShippingQuoteService quotes;
    public ShippingLocationController(GhnClient client, ShippingQuoteService quotes) { this.client=client; this.quotes=quotes; }
    @GetMapping("/provinces") public List<GhnLocations.Province> provinces() { return client.getProvinces(); }
    @GetMapping("/districts") public List<GhnLocations.District> districts(@RequestParam("province_id") int id) {
        if (id <= 0) throw new com.stockflow.common.exception.BadRequestException("Invalid province");
        return client.getDistricts(id);
    }
    @GetMapping("/wards") public List<GhnLocations.Ward> wards(@RequestParam("district_id") int id) {
        if (id <= 0) throw new com.stockflow.common.exception.BadRequestException("Invalid district");
        return client.getWards(id);
    }
    public record FeeRequest(@JsonProperty("warehouse_id") @NotNull @Positive Long warehouseId,
            @JsonProperty("to_district_id") @NotNull @Positive Integer district,
            @JsonProperty("to_ward_code") @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,20}") String ward,
            @NotNull @Min(1) @Max(19999) Integer weight) {}
    public record FeeResponse(@JsonProperty("shipping_fee") BigDecimal fee, @JsonProperty("service_type_id") int service) {}
    @PostMapping("/calculate-fee") public FeeResponse fee(@Valid @RequestBody FeeRequest request) {
        return new FeeResponse(quotes.quote(request.warehouseId(), request.district(), request.ward(), request.weight()), 2);
    }
}
