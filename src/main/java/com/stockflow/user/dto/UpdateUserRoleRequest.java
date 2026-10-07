package com.stockflow.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateUserRoleRequest(
    @NotNull @Pattern(regexp="CUSTOMER|WAREHOUSE_STAFF|MANAGER") String role,
    @NotNull @Size(max=20) @JsonProperty("warehouse_ids") List<@NotNull @Positive Long> warehouseIds) {}
